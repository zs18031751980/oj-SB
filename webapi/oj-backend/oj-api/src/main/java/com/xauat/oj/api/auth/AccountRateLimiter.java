package com.xauat.oj.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** 登录账号维度限流（与旧后端的 account_max_requests 一致）；Redis 故障时放行。 */
@Component
public class AccountRateLimiter {
    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;

    public AccountRateLimiter(StringRedisTemplate redis,
                              @Value("${oj.rate-limit.login-account-per-minute:20}") int limit,
                              @Value("${oj.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redis = redis; this.limit = limit; this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean allow(String identifier) {
        if (identifier == null || identifier.isBlank()) return true;
        try {
            String account = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identifier.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
            String key = "oj:rate:login:account:" + account;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, window);
            return count == null || count <= limit;
        } catch (Exception exception) {
            return true;
        }
    }
}
