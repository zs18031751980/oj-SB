package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 从 {@code Authorization: Bearer} 头解析当前登录用户。
 *
 * <p>作为控制器侧的轻量入口，它与 {@code JwtAuthenticationFilter} 使用相同的“JWT 签名 + 服务端会话”
 * 双重校验规则，确保任何入口拿到的都是同一份可信用户上下文。</p>
 */
@Component
public class CurrentUser {
    private final JwtService jwtService;
    private final UserRepository users;
    private final AuthSessionRepository sessions;

    public CurrentUser(JwtService jwtService, UserRepository users, AuthSessionRepository sessions) {
        this.jwtService = jwtService;
        this.users = users;
        this.sessions = sessions;
    }

    /**
     * 要求已登录，未认证时抛出 {@code UNAUTHORIZED}。
     *
     * @param authorization Bearer 令牌
     * @return 当前用户
     * @throws com.xauat.oj.common.exception.OjException 当令牌无效或已过期
     */
    public User require(String authorization) {
        User user = optional(authorization);
        if (user == null) throw new com.xauat.oj.common.exception.OjException("UNAUTHORIZED", "令牌无效或已过期");
        return user;
    }

    /**
     * 访问令牌必须同时通过签名校验与服务端会话校验（撤销/过期/账号状态）。
     *
     * @param authorization Bearer 令牌，可为 null
     * @return 有效用户；任一校验失败返回 {@code null}
     */
    public User optional(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        try {
            Claims claims = jwtService.parseAccess(authorization.substring(7));
            // 仅凭 JWT 签名不足以判定在线状态，必须回查会话 id 才能感知撤销与过期
            String sid = claims.get("sid", String.class);
            if (sid == null) return null;
            AuthSession session = sessions.findById(sid).orElse(null);
            if (session == null || session.isRevoked() || session.getExpiresAt() == null
                    || !session.getExpiresAt().isAfter(LocalDateTime.now()) || session.getUserId() == null) {
                return null;
            }
            // 校验令牌主体与会话归属一致，防止令牌被替换到其它会话
            Integer userId = Integer.valueOf(claims.getSubject());
            if (!userId.equals(session.getUserId())) return null;
            return users.findById(userId).filter(User::isActive).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
