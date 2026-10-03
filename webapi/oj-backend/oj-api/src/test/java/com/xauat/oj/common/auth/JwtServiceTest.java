package com.xauat.oj.common.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    private final JwtService jwt = new JwtService("0123456789abcdef0123456789abcdef", 900, "letcoding", "letcoding-api");

    @Test
    void accessTokenCarriesSessionClaims() {
        String token = jwt.issueAccessToken(7, "sid-1", "alice", "manager");
        Claims claims = jwt.parseAccess(token);
        assertEquals("7", claims.getSubject());
        assertEquals(7, claims.get("user_id", Integer.class));
        assertEquals("sid-1", claims.get("sid", String.class));
        assertEquals("access", claims.get("type", String.class));
        assertEquals("letcoding", claims.getIssuer());
        assertEquals("letcoding-api", claims.getAudience().iterator().next());
        assertEquals("manager", claims.get("role", String.class));
        if (claims.getId() == null) throw new AssertionError("jti missing");
    }

    @Test
    void refreshTokenIsSeparateType() {
        String refresh = jwt.issueRefreshToken(7, "sid-1", Instant.now().plusSeconds(60));
        Claims claims = jwt.parseRefresh(refresh);
        assertEquals("refresh", claims.get("type", String.class));
        assertThrows(RuntimeException.class, () -> jwt.parseAccess(refresh));
    }
}
