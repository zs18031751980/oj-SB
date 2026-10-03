package com.xauat.oj.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.constant.RequestIdConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean requireWorker;
    private final long workerFreshSeconds;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redis, ObjectMapper mapper,
                            @Value("${oj.readiness.require-worker:false}") boolean requireWorker,
                            @Value("${oj.readiness.worker-fresh-seconds:45}") long workerFreshSeconds) {
        this.jdbcTemplate = jdbcTemplate; this.redis = redis; this.mapper = mapper;
        this.requireWorker = requireWorker; this.workerFreshSeconds = workerFreshSeconds;
    }

    @GetMapping({"/", "/healthz"})
    public Map<String, Object> health(HttpServletRequest request) { return body("ok", request); }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> ready(HttpServletRequest request) {
        try { jdbcTemplate.queryForObject("select 1", Integer.class); }
        catch (RuntimeException exception) { return ResponseEntity.status(503).body(body("database unavailable", request)); }
        try { redis.getConnectionFactory().getConnection().ping(); }
        catch (RuntimeException exception) { return ResponseEntity.status(503).body(body("redis unavailable", request)); }
        if (requireWorker && freshWorkerCount() == 0) {
            return ResponseEntity.status(503).body(body("no judge worker available", request));
        }
        return ResponseEntity.ok(body("ready", request));
    }

    @GetMapping("/healthz/db")
    public ResponseEntity<Map<String, Object>> database(HttpServletRequest request) {
        try { jdbcTemplate.queryForObject("select 1", Integer.class); return ResponseEntity.ok(body("ok", request)); }
        catch (RuntimeException exception) { return ResponseEntity.status(503).body(body("database unavailable", request)); }
    }

    private long freshWorkerCount() {
        try {
            long now = Instant.now().getEpochSecond();
            Set<String> keys = redis.keys("judge:worker:*");
            if (keys == null) return 0;
            long fresh = 0;
            for (String key : keys) {
                String value = redis.opsForValue().get(key);
                if (value == null) continue;
                var node = mapper.readTree(value);
                long stamp = node.path("heartbeat_unix").asLong(0);
                if (now - stamp <= workerFreshSeconds) fresh++;
            }
            return fresh;
        } catch (Exception exception) {
            return 0;
        }
    }

    private Map<String, Object> body(String status, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("request_id", request.getAttribute(RequestIdConstants.ATTRIBUTE));
        return body;
    }
}
