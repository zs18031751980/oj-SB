package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

    public User require(String authorization) {
        User user = optional(authorization);
        if (user == null) throw new com.xauat.oj.common.exception.OjException("UNAUTHORIZED", "令牌无效或已过期");
        return user;
    }

    /** 访问令牌必须同时通过签名校验与服务端会话校验（撤销/过期/账号状态）。 */
    public User optional(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        try {
            Claims claims = jwtService.parseAccess(authorization.substring(7));
            String sid = claims.get("sid", String.class);
            if (sid == null) return null;
            AuthSession session = sessions.findById(sid).orElse(null);
            if (session == null || session.isRevoked() || session.getExpiresAt() == null
                    || !session.getExpiresAt().isAfter(LocalDateTime.now()) || session.getUserId() == null) {
                return null;
            }
            Integer userId = Integer.valueOf(claims.getSubject());
            if (!userId.equals(session.getUserId())) return null;
            return users.findById(userId).filter(User::isActive).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
