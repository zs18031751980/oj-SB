package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.OAuthGrant;
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
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class OAuthController {
    private final StringRedisTemplate redis; private final UserRepository users; private final OAuthGrantRepository grants; private final JwtService jwt; private final ObjectMapper mapper; private final String frontend;
    private final OAuthProviderRegistry providers; private final AuthSessionService sessionService; private final ProviderUserSync providerUserSync;
    private final boolean pkceEnabled;
    public OAuthController(StringRedisTemplate redis, UserRepository users, OAuthGrantRepository grants, JwtService jwt, ObjectMapper mapper,
                           @Value("${oj.auth.frontend-url:http://localhost:5173}") String frontend,
                           OAuthProviderRegistry providers, AuthSessionService sessionService, ProviderUserSync providerUserSync,
                           @Value("${oj.oauth.pkce:false}") boolean pkceEnabled) {
        this.redis=redis; this.users=users; this.grants=grants; this.jwt=jwt; this.mapper=mapper; this.frontend=frontend; this.providers=providers; this.sessionService=sessionService; this.providerUserSync=providerUserSync; this.pkceEnabled=pkceEnabled;
    }

    @GetMapping("/login/{provider}")
    public void login(@PathVariable String provider, @RequestParam(defaultValue="/") String next, HttpServletResponse response) throws Exception {
        String authorization = providers.property(provider, "authorization-uri"); if (authorization == null) { response.sendError(503, "OAuth provider 未配置"); return; }
        String state = UUID.randomUUID().toString(); redis.opsForValue().set("oj:oauth:state:" + state, provider + "\n" + next, Duration.ofMinutes(5));
        String challenge = null;
        if (pkceEnabled) {
            String verifier = base64Url(java.security.SecureRandom.getInstanceStrong().generateSeed(48));
            challenge = base64Url(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            redis.opsForValue().set("oj:oauth:pkce:" + state, verifier, Duration.ofMinutes(5));
        }
        String redirect = providers.authorizationUrl(provider, null, state, challenge);
        response.sendRedirect(redirect);
    }

    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable String provider, @RequestParam String code, @RequestParam String state, HttpServletResponse response) throws Exception {
        String stored = redis.opsForValue().getAndDelete("oj:oauth:state:" + state); if (stored == null || !stored.startsWith(provider + "\n")) { response.sendError(400, "OAuth state 无效"); return; }
        String verifier = pkceEnabled ? redis.opsForValue().getAndDelete("oj:oauth:pkce:" + state) : null;
        JsonNode profile = exchange(provider, code, verifier); String providerId = profile.path("sub").asText(profile.path("id").asText()); String email = profile.path("email").asText(""); String username = profile.path("preferred_username").asText(profile.path("login").asText(email)); if (providerId.isBlank() || username.isBlank()) { response.sendError(400, "OAuth 用户信息不完整"); return; }
        User user = providerUserSync.sync(provider, providerId, profile, username, email, profile.path("picture").asText(""));
        if (!user.isActive()) { response.sendError(403, "账号已被停用"); return; }
        String exchangeCode = UUID.randomUUID().toString(); grants.save(OAuthGrant.create(UUID.randomUUID().toString(), user, JwtService.digest(exchangeCode), LocalDateTime.now().plusMinutes(2))); redis.opsForValue().set("oj:oauth:exchange:" + exchangeCode, user.getId().toString(), Duration.ofMinutes(2)); String next = stored.substring(provider.length()+1); response.sendRedirect(frontend + "/auth/callback?code=" + OAuthProviderRegistry.enc(exchangeCode) + "&next=" + OAuthProviderRegistry.enc(next));
    }

    @PostMapping("/exchange") @Transactional
    public ResponseEntity<?> exchange(@RequestBody ExchangeRequest request, HttpServletResponse response) {
        String userId = redis.opsForValue().getAndDelete("oj:oauth:exchange:" + request.code()); if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "exchange code 无效或已过期")); var grant = grants.findByBindingHash(JwtService.digest(request.code())).filter(item -> item.usable(JwtService.digest(request.code()))).orElse(null); if (grant == null) return ResponseEntity.status(401).body(Map.of("error", "OAuth Grant 无效或已过期")); var user = users.findById(Integer.valueOf(userId)).orElse(null); if (user == null || !user.isActive() || !user.getId().equals(grant.getUser().getId())) return ResponseEntity.status(401).build();
        var issued = sessionService.issue(user);
        sessionService.setRefreshCookie(response, issued.refreshToken(), request.remember() == null || request.remember());
        return ResponseEntity.ok(sessionBody(user, issued));
    }

    @PostMapping("/login/{provider}/password") @Transactional
    public ResponseEntity<?> providerPassword(@PathVariable String provider, @RequestBody PasswordRequest request, HttpServletResponse response) {
        String tokenUri = providers.property(provider, "token-uri"); if (tokenUri == null) return ResponseEntity.status(503).body(Map.of("error", "OAuth provider 未配置"));
        try {
            String form = "grant_type=password&username=" + OAuthProviderRegistry.enc(request.identifier()) + "&password=" + OAuthProviderRegistry.enc(request.password()) + "&client_id=" + OAuthProviderRegistry.enc(providers.property(provider, "client-id")) + "&client_secret=" + OAuthProviderRegistry.enc(providers.property(provider, "client-secret"));
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(tokenUri)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
            JsonNode token = mapper.readTree(HttpClient.newHttpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body()); String access = token.path("access_token").asText(); if (access.isBlank()) return ResponseEntity.status(401).body(Map.of("error", "提供商认证失败"));
            HttpRequest infoRequest = HttpRequest.newBuilder(URI.create(providers.property(provider, "userinfo-uri"))).header("Authorization", "Bearer " + access).GET().build(); JsonNode profile = mapper.readTree(HttpClient.newHttpClient().send(infoRequest, HttpResponse.BodyHandlers.ofString()).body()); String providerId = profile.path("sub").asText(profile.path("id").asText()); String email = profile.path("email").asText(""); String username = profile.path("preferred_username").asText(profile.path("login").asText(email)); if (providerId.isBlank() || username.isBlank()) return ResponseEntity.status(502).body(Map.of("error", "提供商用户信息不完整"));
            User user = providerUserSync.sync(provider, providerId, profile, username, email, profile.path("picture").asText(""));
            if (!user.isActive()) return ResponseEntity.status(403).body(Map.of("error", "账号已被停用"));
            var issued = sessionService.issue(user);
            sessionService.setRefreshCookie(response, issued.refreshToken(), request.remember() == null || request.remember());
            return ResponseEntity.ok(sessionBody(user, issued));
        } catch (Exception exception) { return ResponseEntity.status(502).body(Map.of("error", "提供商认证请求失败")); }
    }

    private Map<String, Object> sessionBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> userInfo = sessionService.userInfo(user);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("access_token", issued.accessToken());
        tokens.put("expires_in", issued.expiresIn());
        tokens.put("token_type", "Bearer");
        tokens.put("user_info", userInfo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("access_token", issued.accessToken());
        body.put("expires_in", issued.expiresIn());
        body.put("token_type", "Bearer");
        body.put("user_info", userInfo);
        body.put("tokens", tokens);
        return body;
    }

    private JsonNode exchange(String provider, String code, String codeVerifier) throws Exception { String tokenUri=providers.property(provider,"token-uri"), clientId=providers.property(provider,"client-id"), secret=providers.property(provider,"client-secret"); String form="grant_type=authorization_code&code="+OAuthProviderRegistry.enc(code)+"&client_id="+OAuthProviderRegistry.enc(clientId)+"&client_secret="+OAuthProviderRegistry.enc(secret)+"&redirect_uri="+OAuthProviderRegistry.enc(providers.property(provider,"redirect-uri"))+(codeVerifier==null?"":"&code_verifier="+OAuthProviderRegistry.enc(codeVerifier)); HttpRequest req=HttpRequest.newBuilder(URI.create(tokenUri)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(); JsonNode token=mapper.readTree(HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString()).body()); String access=token.path("access_token").asText(); HttpRequest info=HttpRequest.newBuilder(URI.create(providers.property(provider,"userinfo-uri"))).header("Authorization","Bearer "+access).GET().build(); return mapper.readTree(HttpClient.newHttpClient().send(info,HttpResponse.BodyHandlers.ofString()).body()); }

    private static String base64Url(byte[] value) { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value); }

    public record ExchangeRequest(String code, Boolean remember) {}
    public record PasswordRequest(String identifier, String password, Boolean remember) {}
}
