package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.domain.OAuthGrant;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.auth.repository.OAuthGrantRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class OAuthController {
    private final StringRedisTemplate redis; private final UserRepository users; private final OAuthGrantRepository grants; private final AuthSessionRepository sessions; private final JwtService jwt; private final ObjectMapper mapper; private final String frontend;
    public OAuthController(StringRedisTemplate redis, UserRepository users, OAuthGrantRepository grants, AuthSessionRepository sessions, JwtService jwt, ObjectMapper mapper, @Value("${oj.auth.frontend-url:http://localhost:5173}") String frontend) { this.redis=redis; this.users=users; this.grants=grants; this.sessions=sessions; this.jwt=jwt; this.mapper=mapper; this.frontend=frontend; }

    @GetMapping("/login/{provider}")
    public void login(@PathVariable String provider, @RequestParam(defaultValue="/") String next, HttpServletResponse response) throws Exception {
        String authorization = property(provider, "authorization-uri"); if (authorization == null) { response.sendError(503, "OAuth provider 未配置"); return; }
        String state = UUID.randomUUID().toString(); redis.opsForValue().set("oj:oauth:state:" + state, provider + "\n" + next, Duration.ofMinutes(5));
        String redirect = authorization + "?response_type=code&client_id=" + enc(property(provider,"client-id")) + "&redirect_uri=" + enc(property(provider,"redirect-uri")) + "&scope=" + enc("openid profile email") + "&state=" + enc(state); response.sendRedirect(redirect);
    }

    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable String provider, @RequestParam String code, @RequestParam String state, HttpServletResponse response) throws Exception {
        String stored = redis.opsForValue().getAndDelete("oj:oauth:state:" + state); if (stored == null || !stored.startsWith(provider + "\n")) { response.sendError(400, "OAuth state 无效"); return; }
        JsonNode profile = exchange(provider, code); String providerId = profile.path("sub").asText(profile.path("id").asText()); String email = profile.path("email").asText(""); String username = profile.path("preferred_username").asText(profile.path("login").asText(email)); if (providerId.isBlank() || username.isBlank()) { response.sendError(400, "OAuth 用户信息不完整"); return; }
        User user = users.findByProviderAndProviderId(provider, providerId).orElseGet(() -> users.save(User.external(username, email, provider, providerId, profile.path("picture").asText(""))));
        String exchangeCode = UUID.randomUUID().toString(); grants.save(OAuthGrant.create(UUID.randomUUID().toString(), user, JwtService.digest(exchangeCode), LocalDateTime.now().plusMinutes(2))); redis.opsForValue().set("oj:oauth:exchange:" + exchangeCode, user.getId().toString(), Duration.ofMinutes(2)); String next = stored.substring(provider.length()+1); response.sendRedirect(frontend + "/auth/callback?code=" + enc(exchangeCode) + "&next=" + enc(next));
    }

    @PostMapping("/exchange") @Transactional
    public ResponseEntity<?> exchange(@RequestBody ExchangeRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String userId = redis.opsForValue().getAndDelete("oj:oauth:exchange:" + request.code()); if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "exchange code 无效或已过期")); var grant = grants.findByBindingHash(JwtService.digest(request.code())).filter(item -> item.usable(JwtService.digest(request.code()))).orElse(null); if (grant == null) return ResponseEntity.status(401).body(Map.of("error", "OAuth Grant 无效或已过期")); var user = users.findById(Integer.valueOf(userId)).orElse(null); if (user == null || !user.isActive() || !user.getId().equals(grant.getUser().getId())) return ResponseEntity.status(401).build(); String refresh = UUID.randomUUID().toString(); sessions.save(AuthSession.create(UUID.randomUUID().toString(), user, JwtService.digest(refresh), LocalDateTime.now().plusDays(30))); CookieUtil.setRefresh(response, refresh); String access = jwt.issueAccessToken(user.getId(), user.getUsername(), user.getRole()); return ResponseEntity.ok(Map.of("success", true, "access_token", access, "token_type", "Bearer", "expires_in", 900, "tokens", Map.of("access_token", access, "token_type", "Bearer", "expires_in", 900)));
    }

    @PostMapping("/login/{provider}/password") @Transactional
    public ResponseEntity<?> providerPassword(@PathVariable String provider, @RequestBody PasswordRequest request, HttpServletResponse response) {
        String tokenUri = property(provider, "token-uri"); if (tokenUri == null) return ResponseEntity.status(503).body(Map.of("error", "OAuth provider 未配置"));
        try {
            String form = "grant_type=password&username=" + enc(request.identifier()) + "&password=" + enc(request.password()) + "&client_id=" + enc(property(provider, "client-id")) + "&client_secret=" + enc(property(provider, "client-secret"));
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(tokenUri)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            JsonNode token = mapper.readTree(HttpClient.newHttpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body()); String access = token.path("access_token").asText(); if (access.isBlank()) return ResponseEntity.status(401).body(Map.of("error", "提供商认证失败"));
            HttpRequest infoRequest = HttpRequest.newBuilder(URI.create(property(provider, "userinfo-uri"))).header("Authorization", "Bearer " + access).GET().build(); JsonNode profile = mapper.readTree(HttpClient.newHttpClient().send(infoRequest, HttpResponse.BodyHandlers.ofString()).body()); String providerId = profile.path("sub").asText(profile.path("id").asText()); String email = profile.path("email").asText(""); String username = profile.path("preferred_username").asText(profile.path("login").asText(email)); if (providerId.isBlank() || username.isBlank()) return ResponseEntity.status(502).body(Map.of("error", "提供商用户信息不完整"));
            User user = users.findByProviderAndProviderId(provider, providerId).orElseGet(() -> users.save(User.external(username, email, provider, providerId, profile.path("picture").asText("")))); String refresh = UUID.randomUUID().toString(); sessions.save(AuthSession.create(UUID.randomUUID().toString(), user, JwtService.digest(refresh), LocalDateTime.now().plusDays(30))); CookieUtil.setRefresh(response, refresh); return ResponseEntity.ok(Map.of("access_token", jwt.issueAccessToken(user.getId(), user.getUsername(), user.getRole()), "token_type", "Bearer", "expires_in", 900));
        } catch (Exception exception) { return ResponseEntity.status(502).body(Map.of("error", "提供商认证请求失败")); }
    }

    private JsonNode exchange(String provider, String code) throws Exception { String tokenUri=property(provider,"token-uri"), clientId=property(provider,"client-id"), secret=property(provider,"client-secret"); String form="grant_type=authorization_code&code="+enc(code)+"&client_id="+enc(clientId)+"&client_secret="+enc(secret)+"&redirect_uri="+enc(property(provider,"redirect-uri")); HttpRequest req=HttpRequest.newBuilder(URI.create(tokenUri)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(); JsonNode token=mapper.readTree(HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()).body()); String access=token.path("access_token").asText(); HttpRequest info=HttpRequest.newBuilder(URI.create(property(provider,"userinfo-uri"))).header("Authorization","Bearer "+access).GET().build(); return mapper.readTree(HttpClient.newHttpClient().send(info,HttpResponse.BodyHandlers.ofString()).body()); }
    private String property(String provider,String key) { return System.getenv(("OJ_OAUTH_"+provider+"_"+key).toUpperCase(Locale.ROOT).replace('-','_')); }
    private static String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    public record ExchangeRequest(String code, boolean remember) {}
    public record PasswordRequest(String identifier, String password) {}
    static final class CookieUtil { static void setRefresh(HttpServletResponse response,String value) { jakarta.servlet.http.Cookie c=new jakarta.servlet.http.Cookie("refresh_token",value); c.setHttpOnly(true); c.setSecure(true); c.setPath("/"); c.setMaxAge(2592000); response.addCookie(c); } }
}
