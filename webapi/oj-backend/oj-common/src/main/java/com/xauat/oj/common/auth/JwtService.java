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
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${oj.auth.jwt-secret}") String secret,
                      @Value("${oj.auth.access-ttl-seconds:900}") long accessTtlSeconds) {
        if (secret.length() < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
    }

    public String issueAccessToken(Integer userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(userId)).claims(Map.of("username", username == null ? "" : username,
                        "role", role == null ? "member" : role)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl))).signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算凭证摘要", exception); }
    }
}
