package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 会话校验测试：登出/撤销、过期、会话归属不符与账号停用后，旧访问令牌必须失效。
 */
class CurrentUserSessionTest {
    private JwtService jwt;
    private UserRepository users;
    private AuthSessionRepository sessions;
    private Claims claims;
    private AuthSession session;
    private User user;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        users = mock(UserRepository.class);
        sessions = mock(AuthSessionRepository.class);
        claims = mock(Claims.class);
        session = mock(AuthSession.class);
        user = mock(User.class);
        currentUser = new CurrentUser(jwt, users, sessions);

        when(jwt.parseAccess("token")).thenReturn(claims);
        when(claims.get("sid", String.class)).thenReturn("sid-1");
        when(claims.getSubject()).thenReturn("1");
        when(sessions.findById("sid-1")).thenReturn(Optional.of(session));
        when(session.isRevoked()).thenReturn(false);
        when(session.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(5));
        when(session.getUserId()).thenReturn(1);
        when(users.findById(1)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
    }

    @Test
    void validSessionReturnsUser() {
        assertSame(user, currentUser.optional("Bearer token"));
    }

    @Test
    void revokedSessionIsRejected() {
        when(session.isRevoked()).thenReturn(true);
        assertNull(currentUser.optional("Bearer token"));
    }

    @Test
    void expiredSessionIsRejected() {
        when(session.getExpiresAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        assertNull(currentUser.optional("Bearer token"));
    }

    @Test
    void sessionOwnerMismatchIsRejected() {
        when(session.getUserId()).thenReturn(2);
        assertNull(currentUser.optional("Bearer token"));
    }

    @Test
    void inactiveUserIsRejected() {
        when(user.isActive()).thenReturn(false);
        assertNull(currentUser.optional("Bearer token"));
    }

    @Test
    void malformedOrMissingTokenIsRejected() {
        assertNull(currentUser.optional(null));
        assertNull(currentUser.optional("Basic abc"));
    }
}
