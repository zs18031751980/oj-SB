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

    /**
     * @param redis         用于计数的 Redis 模板
     * @param limit         单个窗口内允许的登录尝试次数
     * @param windowSeconds 计数窗口长度（秒）
     */
    public AccountRateLimiter(StringRedisTemplate redis,
                              @Value("${oj.rate-limit.login-account-per-minute:20}") int limit,
                              @Value("${oj.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redis = redis; this.limit = limit; this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * 判断给定账号标识（用户名或邮箱）在当前窗口内是否仍可尝试登录。
     *
     * @param identifier 登录标识
     * @return {@code true} 表示放行；达到上限返回 {@code false}；标识为空或 Redis 异常时放行
     */
    public boolean allow(String identifier) {
        if (identifier == null || identifier.isBlank()) return true;
        try {
            // 对标识做归一化后取 SHA-256，避免把明文账号写进 Redis key，也统一大小写与首尾空格
            String account = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identifier.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
            String key = "oj:rate:login:account:" + account;
            Long count = redis.opsForValue().increment(key);
            // 仅在首次自增（=1）时设置过期时间，避免每次请求都刷新 TTL 导致窗口无法滑出
            if (count != null && count == 1L) redis.expire(key, window);
            return count == null || count <= limit;
        } catch (Exception exception) {
            // 限流属于保护性措施，Redis 不可用时选择放行，不应阻断正常登录
            return true;
        }
    }
}
