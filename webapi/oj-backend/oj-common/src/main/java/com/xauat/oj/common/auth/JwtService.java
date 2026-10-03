package com.xauat.oj.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 与旧 Flask 后端等价的 JWT：保留 user_id/sid/iss/aud/jti/type/exp 声明。
 * Access 与 Refresh 使用同一签名密钥，通过 type 区分，且必须结合 auth_sessions 校验。
 */
@Component
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final String issuer;
    private final String audience;

    public JwtService(@Value("${oj.auth.jwt-secret}") String secret,
                      @Value("${oj.auth.access-ttl-seconds:900}") long accessTtlSeconds,
                      @Value("${oj.auth.issuer:letcoding}") String issuer,
                      @Value("${oj.auth.audience:letcoding-api}") String audience) {
        if (secret.length() < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.issuer = issuer;
        this.audience = audience;
    }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }

    /** 签发访问令牌：携带 sid 以便服务端按会话撤销。 */
    public String issueAccessToken(Integer userId, String sid, String username, String role) {
        return issueAccessToken(userId, sid, username, role, Instant.now().plus(accessTtl));
    }

    public String issueAccessToken(Integer userId, String sid, String username, String role, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("user_id", userId)
                .claim("sid", sid)
                .claim("username", username == null ? "" : username)
                .claim("role", role == null ? "member" : role)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** 签发刷新令牌，过期时间与 auth_sessions.expires_at 保持一致。 */
    public String issueRefreshToken(Integer userId, String sid, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("user_id", userId)
                .claim("sid", sid)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Claims parseAccess(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("type", String.class))) throw new IllegalArgumentException("not an access token");
        return claims;
    }

    public Claims parseRefresh(String token) {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("type", String.class))) throw new IllegalArgumentException("not a refresh token");
        return claims;
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience)
                .build().parseSignedClaims(token).getPayload();
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算凭证摘要", exception); }
    }
}
