package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionRepository sessions;
    private final AuthSessionService sessionService;
    private final OAuthProviderRegistry providers;
    private final CurrentUser currentUser;
    private final AccountRateLimiter rateLimiter;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuthSessionRepository sessions, AuthSessionService sessionService,
                          OAuthProviderRegistry providers, CurrentUser currentUser, AccountRateLimiter rateLimiter) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService;
        this.sessions = sessions; this.sessionService = sessionService; this.providers = providers;
        this.currentUser = currentUser; this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login/password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (!rateLimiter.allow(request.identifier())) {
            return ResponseEntity.status(429).body(Map.of("error", "请求过于频繁，请稍后再试"));
        }
        User user = users.findByUsername(request.identifier()).orElseGet(() -> users.findByEmail(request.identifier()).orElse(null));
        if (user == null || !user.isActive() || user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.updatePasswordHash(passwordEncoder.encode(request.password()));
        }
        user.markLogin();
        users.save(user);
        var issued = sessionService.issue(user);
        sessionService.setRefreshCookie(response, issued.refreshToken(), request.remember() == null || request.remember());
        return ResponseEntity.ok(loginBody(user, issued));
    }

    /** API 客户端 OAuth 登录：返回授权地址，而不是执行密码登录。 */
    @PostMapping("/login")
    public ResponseEntity<?> loginAlias(@RequestBody LoginAliasRequest request) {
        if (request.provider() == null || request.provider().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请输入登录方式"));
        }
        if (!providers.configured(request.provider())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "不支持的登录方式：" + request.provider()));
        }
        String authorizationUrl = providers.authorizationUrl(request.provider(), request.redirectUri(), UUID.randomUUID().toString());
        if (authorizationUrl == null) return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "创建授权地址失败"));
        return ResponseEntity.ok(Map.of("success", true, "authorization_url", authorizationUrl));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUser.optional(authorization);
        if (user == null) return ResponseEntity.status(401).body(Map.of("valid", false, "error", "令牌无效或已过期"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", true);
        body.put("user_info", sessionService.userInfo(user));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        String configured = System.getenv().getOrDefault("OJ_OAUTH_PROVIDERS", "github,google,gitee,oidc");
        var result = java.util.Arrays.stream(configured.split(",")).map(String::trim)
                .filter(name -> !name.isBlank()).filter(providers::configured)
                .map(name -> Map.of("id", name, "name", name)).toList();
        return Map.of("providers", result);
    }

    @PatchMapping("/theme")
    public ResponseEntity<?> theme(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody ThemeRequest request) {
        if (!Set.of("light", "dark", "system").contains(request.theme())) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid theme_preference, must be light, dark, or system"));
        }
        User user = currentUser.require(authorization);
        user.updateProfile(null, null, request.theme());
        users.save(user);
        return ResponseEntity.ok(Map.of("success", true, "theme_preference", request.theme()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = AuthSessionService.REFRESH_COOKIE, required = false) String refreshToken,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpServletResponse response) {
        if (refreshToken != null) revokeByRefreshToken(refreshToken);
        if (authorization != null && authorization.startsWith("Bearer ")) revokeByAccessToken(authorization.substring(7));
        sessionService.clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "logout successful"));
    }

    @PostMapping("/refresh")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refresh(@CookieValue(value = AuthSessionService.REFRESH_COOKIE, required = false) String refreshToken,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String requestId,
                                     @RequestBody(required = false) RememberRequest body,
                                     HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is required"));
        String sid;
        try { sid = jwtService.parseRefresh(refreshToken).get("sid", String.class); }
        catch (RuntimeException exception) { return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired")); }
        AuthSession session = sessions.findForUpdateById(sid).orElse(null);
        if (session == null || session.isRevoked() || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now()) || session.getUserId() == null) {
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        User user = users.findById(session.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            session.revoke(); sessions.save(session);
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        String digest = JwtService.digest(refreshToken);
        var recovered = sessionService.recover(session, digest, requestId);
        if (recovered.isPresent()) {
            var issued = recovered.get();
            return ResponseEntity.ok(tokenBody(user, issued));
        }
        if (!digest.equals(session.getRefreshHash())) {
            session.revoke(); sessions.save(session);
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        var issued = sessionService.rotate(session, requestId);
        boolean remember = body == null || body.remember() == null || body.remember();
        sessionService.setRefreshCookie(response, issued.refreshToken(), remember);
        return ResponseEntity.ok(tokenBody(user, issued));
    }

    private void revokeByRefreshToken(String refreshToken) {
        try {
            String sid = jwtService.parseRefresh(refreshToken).get("sid", String.class);
            sessions.findById(sid).ifPresent(session -> { session.revoke(); sessions.save(session); });
        } catch (RuntimeException ignored) { }
    }

    private void revokeByAccessToken(String accessToken) {
        try {
            String sid = jwtService.parseAccess(accessToken).get("sid", String.class);
            sessions.findById(sid).ifPresent(session -> { session.revoke(); sessions.save(session); });
        } catch (RuntimeException ignored) { }
    }

    private Map<String, Object> loginBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> userInfo = sessionService.userInfo(user);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("access_token", issued.accessToken());
        tokens.put("expires_in", issued.expiresIn());
        tokens.put("token_type", "Bearer");
        tokens.put("user_info", userInfo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("user_info", userInfo);
        body.put("tokens", tokens);
        return body;
    }

    private Map<String, Object> tokenBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", issued.accessToken());
        body.put("expires_in", issued.expiresIn());
        body.put("token_type", "Bearer");
        body.put("user_info", sessionService.userInfo(user));
        return body;
    }

    public record LoginRequest(@JsonAlias({"username"}) @NotBlank String identifier, @NotBlank String password, Boolean remember) {}
    public record LoginAliasRequest(String provider, String redirectUri) {}
    public record RememberRequest(Boolean remember) {}
    public record ThemeRequest(@JsonAlias({"theme", "theme_preference"}) @NotBlank String theme) {}
}
