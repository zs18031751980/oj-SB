package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 周期性写入 judge:worker:&lt;id&gt; 心跳，供就绪检查与监控判断 Worker 是否在线接单。 */
@Component
public class WorkerHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeat.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String workerId = UUID.randomUUID().toString();
    private final Duration ttl = Duration.ofSeconds(45);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final String pool;

    public WorkerHeartbeat(StringRedisTemplate redis, ObjectMapper mapper,
                           @Value("${oj.worker.pool:all}") String pool) {
        this.redis = redis; this.mapper = mapper; this.pool = pool;
    }

    public String pool() { return pool; }
    public boolean draining() { return draining.get(); }

    @PostConstruct
    public void start() { beat(); }

    public void beginDrain() { draining.set(true); beat(); }

    @Scheduled(fixedDelayString = "${oj.worker.heartbeat-ms:10000}")
    public void beat() {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alive", true);
            payload.put("heartbeat_unix", Instant.now().getEpochSecond());
            payload.put("pool", pool);
            payload.put("accepting_jobs", !draining.get());
            payload.put("draining", draining.get());
            payload.put("active_job", null);
            redis.opsForValue().set("judge:worker:" + workerId, mapper.writeValueAsString(payload), ttl);
        } catch (Exception exception) {
            log.warn("写出 Worker 心跳失败: {}", exception.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try { redis.delete("judge:worker:" + workerId); }
        catch (RuntimeException ignored) { }
    }
}
