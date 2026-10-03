package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 统一负责会话签发、轮换与浏览器刷新 Cookie，保持与旧后端相同的 Cookie 名与路径。
 */
@Component
public class AuthSessionService {
    public static final String REFRESH_COOKIE = "letcoding_refresh";
    private static final String COOKIE_PATH = "/auth";

    private final AuthSessionRepository sessions;
    private final JwtService jwt;
    private final RefreshRecoveryCipher cipher;
    private final long refreshTtlSeconds;
    private final boolean cookieSecure;

    public AuthSessionService(AuthSessionRepository sessions, JwtService jwt, RefreshRecoveryCipher cipher,
                              @Value("${oj.auth.refresh-ttl-seconds:604800}") long refreshTtlSeconds,
                              @Value("${oj.auth.cookie-secure:false}") boolean cookieSecure) {
        this.sessions = sessions;
        this.jwt = jwt;
        this.cipher = cipher;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.cookieSecure = cookieSecure;
    }

    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {}

    public IssuedTokens issue(User user) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(refreshTtlSeconds);
        String access = jwt.issueAccessToken(user.getId(), sid, user.getUsername(), user.getRole());
        String refresh = jwt.issueRefreshToken(user.getId(), sid, expiresAt);
        sessions.save(AuthSession.create(sid, user, JwtService.digest(refresh),
                LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())));
        return new IssuedTokens(access, refresh, jwt.accessTtlSeconds());
    }

    /** 刷新成功后轮换会话，并保留 30 秒恢复窗口。 */
    public IssuedTokens rotate(AuthSession session, String requestId) {
        String sid = session.getId();
        Instant expiresAt = Instant.now().plusSeconds(refreshTtlSeconds);
        String access = jwt.issueAccessToken(session.getUser().getId(), sid, session.getUser().getUsername(), session.getUser().getRole());
        String refresh = jwt.issueRefreshToken(session.getUser().getId(), sid, expiresAt);
        String recovery = cipher.encrypt(access + "\n" + refresh + "\n" + jwt.accessTtlSeconds());
        session.applyRotation(session.getRefreshHash(), JwtService.digest(refresh), requestId,
                LocalDateTime.now().plusSeconds(30), recovery);
        sessions.save(session);
        return new IssuedTokens(access, refresh, jwt.accessTtlSeconds());
    }

    /** 同一请求 30 秒内的重放：解密并返回上次签发的令牌，避免客户端因响应丢失而被登出。 */
    public Optional<IssuedTokens> recover(AuthSession session, String tokenDigest, String requestId) {
        if (requestId == null || requestId.isBlank() || !requestId.equals(session.getRefreshRequestId())) return Optional.empty();
        if (session.getRefreshRetryUntil() == null || !session.getRefreshRetryUntil().isAfter(LocalDateTime.now())) return Optional.empty();
        boolean digestMatches = tokenDigest.equals(session.getRefreshHash())
                || (session.getPreviousRefreshHash() != null && tokenDigest.equals(session.getPreviousRefreshHash()));
        if (!digestMatches || session.getRefreshRetryCiphertext() == null) return Optional.empty();
        String[] parts = cipher.decrypt(session.getRefreshRetryCiphertext()).split("\n", 3);
        return Optional.of(new IssuedTokens(parts[0], parts[1], Long.parseLong(parts[2])));
    }

    public void setRefreshCookie(HttpServletResponse response, String refreshToken, boolean remember) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(remember ? (int) refreshTtlSeconds : -1);
        response.addCookie(cookie);
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public Map<String, Object> userInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername() == null ? "" : user.getUsername());
        info.put("email", user.getEmail() == null ? "" : user.getEmail());
        info.put("name", user.getName() == null ? "" : user.getName());
        info.put("avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        info.put("role", user.getRole() == null ? "member" : user.getRole());
        info.put("is_active", user.isActive());
        info.put("theme_preference", user.getThemePreference());
        return info;
    }

    public boolean secureCookie() { return cookieSecure; }

    public String roleOf(User user) { return user.getRole() == null ? "member" : user.getRole().toLowerCase(Locale.ROOT); }
}
