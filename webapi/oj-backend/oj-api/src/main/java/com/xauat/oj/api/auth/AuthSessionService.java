package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    /**
     * @param refreshTtlSeconds 刷新令牌有效期（秒），默认 7 天
     * @param cookieSecure      是否给刷新 Cookie 加 Secure 标记（生产 HTTPS 环境应开启）
     */
    public AuthSessionService(AuthSessionRepository sessions, JwtService jwt, RefreshRecoveryCipher cipher,
            @Value("${oj.auth.refresh-ttl-seconds:604800}")
            long refreshTtlSeconds,
            @Value("${oj.auth.cookie-secure:false}")
            boolean cookieSecure) {
        this.sessions = sessions;
        this.jwt = jwt;
        this.cipher = cipher;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.cookieSecure = cookieSecure;
    }

    /**
     * 一次签发的令牌集合。
     *
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌
     * @param expiresIn    访问令牌剩余有效秒数
     */
    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
    }

    /**
     * 为指定用户新建服务端会话，并同步签发访问令牌与刷新令牌。
     *
     * <p>数据库只保存刷新令牌摘要与恢复密文，不保存明文令牌，降低拖库风险。</p>
     *
     * @param user 已通过认证的用户
     * @return 新签发的令牌集合
     */
    public IssuedTokens issue(User user) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(refreshTtlSeconds);
        String access = jwt.issueAccessToken(user.getId(), sid, user.getUsername(), user.getRole());
        String refresh = jwt.issueRefreshToken(user.getId(), sid, expiresAt);
        sessions.save(AuthSession.create(sid, user, JwtService.digest(refresh),
                LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())));
        return new IssuedTokens(access, refresh, jwt.accessTtlSeconds());
    }

    /**
     * 刷新成功后轮换会话，并保留 30 秒恢复窗口。
     *
     * <p>旧刷新哈希保留为 previous，恢复密文用于同请求重放时返回相同结果。</p>
     *
     * @param session   待轮换的会话（调用方已加锁）
     * @param requestId 幂等键
     * @return 轮换后新签发的令牌集合
     */
    public IssuedTokens rotate(AuthSession session, String requestId) {
        String sid = session.getId();
        Instant expiresAt = Instant.now().plusSeconds(refreshTtlSeconds);
        String access = jwt.issueAccessToken(session.getUser().getId(), sid, session.getUser().getUsername(),
                session.getUser().getRole());
        String refresh = jwt.issueRefreshToken(session.getUser().getId(), sid, expiresAt);
        // 把本次结果加密暂存，供 30 秒内相同 Idempotency-Key 的重试原样返回
        String recovery = cipher.encrypt(access + "\n" + refresh + "\n" + jwt.accessTtlSeconds());
        session.applyRotation(session.getRefreshHash(), JwtService.digest(refresh), requestId,
                LocalDateTime.now().plusSeconds(30), recovery);
        sessions.save(session);
        return new IssuedTokens(access, refresh, jwt.accessTtlSeconds());
    }

    /**
     * 同一请求 30 秒内的重放：解密并返回上次签发的令牌，避免客户端因响应丢失而被登出。
     *
     * @param session     当前会话
     * @param tokenDigest 客户端提交刷新令牌的摘要
     * @param requestId   幂等键
     * @return 命中恢复窗口时返回上次令牌，否则 {@link Optional#empty()}
     */
    public Optional<IssuedTokens> recover(AuthSession session, String tokenDigest, String requestId) {
        // 幂等键必须一致且在恢复时限内，否则视为常规（可能非法的）刷新
        if (requestId == null || requestId.isBlank() || !requestId.equals(session.getRefreshRequestId()))
            return Optional.empty();
        if (session.getRefreshRetryUntil() == null || !session.getRefreshRetryUntil().isAfter(LocalDateTime.now()))
            return Optional.empty();
        // 允许当前或上一版刷新摘要，覆盖轮换前后两种重试时机
        boolean digestMatches = tokenDigest.equals(session.getRefreshHash())
                || (session.getPreviousRefreshHash() != null && tokenDigest.equals(session.getPreviousRefreshHash()));
        if (!digestMatches || session.getRefreshRetryCiphertext() == null)
            return Optional.empty();
        String[] parts = cipher.decrypt(session.getRefreshRetryCiphertext()).split("\n", 3);
        return Optional.of(new IssuedTokens(parts[0], parts[1], Long.parseLong(parts[2])));
    }

    /**
     * 写入刷新 Cookie。
     *
     * <p>HttpOnly 防止脚本读取；Path 限定为 {@code /auth}，仅在刷新/登出等端点携带。
     * remember 为 true 时使用持久 Max-Age，否则为会话 Cookie（-1）。</p>
     *
     * @param response     响应
     * @param refreshToken 刷新令牌
     * @param remember     是否长期保存
     */
    public void setRefreshCookie(HttpServletResponse response, String refreshToken, boolean remember) {
        // 显式设置 SameSite=Lax；Secure 由配置控制，生产环境由启动自检强制开启
        var cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true).secure(cookieSecure).path(COOKIE_PATH).sameSite("Lax");
        if (remember)
            cookie.maxAge(java.time.Duration.ofSeconds(refreshTtlSeconds));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    /**
     * 清除刷新 Cookie（置空且 Max-Age=0）。
     *
     * @param response 响应
     */
    public void clearRefreshCookie(HttpServletResponse response) {
        var cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).path(COOKIE_PATH).maxAge(java.time.Duration.ZERO).sameSite("Lax");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    /**
     * 组装对外暴露的用户信息视图（敏感字段如密码哈希不包含在内）。
     *
     * @param user 用户实体
     * @return 用户信息映射
     */
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

    /** @return 刷新 Cookie 是否启用 Secure 标记。 */
    public boolean secureCookie() {
        return cookieSecure;
    }

    /**
     * 归一化用户角色为小写，空角色回退为 member。
     *
     * @param user 用户实体
     * @return 小写角色名
     */
    public String roleOf(User user) {
        return user.getRole() == null ? "member" : user.getRole().toLowerCase(Locale.ROOT);
    }
}
