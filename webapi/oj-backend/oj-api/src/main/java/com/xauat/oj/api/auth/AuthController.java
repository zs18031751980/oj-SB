package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.xauat.oj.api.auth.CurrentUser;

import java.util.Map;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionRepository sessions;
    private final CurrentUser currentUser;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, AuthSessionRepository sessions, CurrentUser currentUser) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService; this.sessions = sessions; this.currentUser = currentUser;
    }

    @PostMapping("/login/password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        User user = users.findByUsername(request.identifier()).orElseGet(() -> users.findByEmail(request.identifier()).orElse(null));
        if (user == null || !user.isActive() || user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        user.markLogin();
        users.save(user);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        sessions.save(AuthSession.create(UUID.randomUUID().toString(), user, JwtService.digest(refreshToken), LocalDateTime.now().plusDays(30)));
        setRefreshCookie(response, refreshToken, 30 * 24 * 3600);
        return ResponseEntity.ok(Map.of("success", true, "access_token", accessToken, "token_type", "Bearer",
                "expires_in", 900, "user_info", userInfo(user), "tokens", tokenView(accessToken)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginAlias(@Valid @RequestBody LoginRequest request, HttpServletResponse response) { return login(request, response); }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            var claims = jwtService.parse(authorization.substring(7));
            return ResponseEntity.ok(Map.of("valid", true, "user_id", Integer.valueOf(claims.getSubject()), "role", claims.get("role", String.class)));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "error", "令牌无效或已过期"));
        }
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        String configured = System.getenv().getOrDefault("OJ_OAUTH_PROVIDERS", "github,google,gitee,oidc");
        var result = java.util.Arrays.stream(configured.split(",")).map(String::trim).filter(name -> !name.isBlank()).filter(this::providerConfigured).map(name -> Map.of("id", name, "name", name)).toList();
        return Map.of("providers", result);
    }

    private boolean providerConfigured(String provider) {
        String prefix = "OJ_OAUTH_" + provider.toUpperCase(java.util.Locale.ROOT).replace('-', '_') + "_";
        return System.getenv(prefix + "AUTHORIZATION_URI") != null && System.getenv(prefix + "CLIENT_ID") != null;
    }

    @PatchMapping("/theme")
    public ResponseEntity<?> theme(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody ThemeRequest request) {
        User user = currentUser.require(authorization); user.updateProfile(null, null, request.theme()); users.save(user);
        return ResponseEntity.ok(userInfo(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken != null) sessions.findByRefreshHashAndRevokedFalse(JwtService.digest(refreshToken)).ifPresent(session -> { session.revoke(); sessions.save(session); });
        Cookie cookie = new Cookie("refresh_token", "");
        cookie.setMaxAge(0); cookie.setHttpOnly(true); cookie.setPath("/");
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) return ResponseEntity.status(401).body(Map.of("error", "刷新凭证缺失"));
        var session = sessions.findByRefreshHashAndRevokedFalse(JwtService.digest(refreshToken)).orElse(null);
        if (session == null || !session.usable(JwtService.digest(refreshToken))) return ResponseEntity.status(401).body(Map.of("error", "刷新凭证无效或已过期"));
        String nextRefresh = UUID.randomUUID().toString(); session.rotate(JwtService.digest(nextRefresh), LocalDateTime.now().plusDays(30)); sessions.save(session); setRefreshCookie(response, nextRefresh, 30 * 24 * 3600);
        var user = session.getUser(); String access = jwtService.issueAccessToken(user.getId(), user.getUsername(), user.getRole()); return ResponseEntity.ok(Map.of("success", true, "access_token", access, "token_type", "Bearer", "expires_in", 900, "user_info", userInfo(user), "tokens", tokenView(access)));
    }

    private void setRefreshCookie(HttpServletResponse response, String value, int maxAge) { Cookie cookie = new Cookie("refresh_token", value); cookie.setHttpOnly(true); cookie.setSecure(true); cookie.setPath("/"); cookie.setMaxAge(maxAge); response.addCookie(cookie); }

    private Map<String, Object> userInfo(User user) {
        return Map.of("id", user.getId(), "username", user.getUsername() == null ? "" : user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail(), "role", user.getRole(), "is_active", user.isActive());
    }
    private Map<String, Object> tokenView(String access) { return Map.of("access_token", access, "token_type", "Bearer", "expires_in", 900); }

    public record LoginRequest(@JsonAlias({"username"}) @NotBlank String identifier, @NotBlank String password) {}
    public record ThemeRequest(@JsonAlias({"theme", "theme_preference"}) @NotBlank String theme) {}
}
