package com.xauat.oj.api.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * 提交结果短缓存：轮询期间减少数据库压力；PostgreSQL 仍是最终事实源。
 * 判题写入终态后由 Worker 主动失效。
 */
@Component
public class SubmissionCache {
    private static final Duration TTL = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SubmissionCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * 读取缓存；Redis 异常时返回空，不影响主流程。
     *
     * @param key 缓存键
     * @return 缓存对象
     */
    public Optional<LinkedHashMap<String, Object>> get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank())
                return Optional.empty();
            return Optional.of(mapper.readValue(value,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                    }));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /** 写入缓存（固定 TTL），失败静默忽略。 */
    public void put(String key, Object body) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(body), TTL);
        } catch (Exception ignored) {
        }
    }

    /** 删除缓存键，供判题写入终态后主动失效。 */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
        }
    }
}
