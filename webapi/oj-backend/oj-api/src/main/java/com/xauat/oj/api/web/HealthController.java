package com.xauat.oj.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.constant.RequestIdConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 健康检查端点：存活（{@code /healthz}）、就绪（{@code /readyz}）与数据库探针。
 *
 * <p>就绪检查会串联数据库、Redis 以及可选的判题 worker 心跳，供负载均衡摘除未就绪实例。</p>
 */
@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean requireWorker;
    private final long workerFreshSeconds;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${oj.readiness.require-worker:false}")
            boolean requireWorker,
            @Value("${oj.readiness.worker-fresh-seconds:45}")
            long workerFreshSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.mapper = mapper;
        this.requireWorker = requireWorker;
        this.workerFreshSeconds = workerFreshSeconds;
    }

    /**
     * 存活探针，只要进程可响应即视为存活。
     *
     * @param request 当前请求
     * @return 固定 ok 状态
     */
    @GetMapping({"/", "/healthz"})
    public Map<String, Object> health(HttpServletRequest request) {
        return body("ok", request);
    }

    /**
     * 就绪探针，依赖任一不可用即返回 503。
     *
     * @param request 当前请求
     * @return 就绪或说明不可用原因的 503
     */
    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> ready(HttpServletRequest request) {
        // 依次探测数据库与 Redis，任一失败立即报告并提供具体依赖名，便于排障
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(body("database unavailable", request));
        }
        try {
            redis.getConnectionFactory().getConnection().ping();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(body("redis unavailable", request));
        }
        // 仅在显式要求时才以判题 worker 可用性作为就绪条件
        if (requireWorker && freshWorkerCount() == 0) {
            return ResponseEntity.status(503).body(body("no judge worker available", request));
        }
        return ResponseEntity.ok(body("ready", request));
    }

    /**
     * 数据库专用探针。
     *
     * @param request 当前请求
     * @return 数据库可达返回 200，否则 503
     */
    @GetMapping("/healthz/db")
    public ResponseEntity<Map<String, Object>> database(HttpServletRequest request) {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(body("ok", request));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(body("database unavailable", request));
        }
    }

    /**
     * 判题 worker 可用性探针：按池检查是否存在新鲜且接单的 worker。
     *
     * <p>与旧后端一致，时间窗为「过去 5 秒至未来 45 秒内」视为新鲜，
     * 且要求 worker 的池匹配（{@code all} 可服务任意池）且未 drain、正在接单。</p>
     *
     * @param pool    查询的 worker 池，默认 contest
     * @param request 当前请求
     * @return 可用返回 200，否则 503；非法池返回 400
     */
    @GetMapping("/healthz/judge")
    public ResponseEntity<Map<String, Object>> judge(@RequestParam(defaultValue = "contest")
    String pool, HttpServletRequest request) {
        if (!Set.of("contest", "practice", "rejudge", "validation", "all").contains(pool))
            return ResponseEntity.badRequest().body(body("invalid pool", request));
        boolean alive;
        try {
            alive = judgeAvailable(pool);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(body("unavailable", request));
        }
        return alive ? ResponseEntity.ok(body("ok", request))
                : ResponseEntity.status(503).body(body("unavailable", request));
    }

    /** 判断指定池是否存在可接单的新鲜 worker；Redis 异常时保守返回 false。 */
    private boolean judgeAvailable(String pool) {
        long now = Instant.now().getEpochSecond();
        Set<String> keys = redis.keys("judge:worker:*");
        if (keys == null)
            return false;
        for (String key : keys) {
            String value = redis.opsForValue().get(key);
            if (value == null)
                continue;
            com.fasterxml.jackson.databind.JsonNode node;
            try {
                node = mapper.readTree(value);
            } catch (Exception parseError) {
                continue;
            }
            long delta = now - node.path("heartbeat_unix").asLong(0);
            // 与旧后端保持同一新鲜窗口：允许时钟轻微前偏，超过 45 秒视为过期
            if (delta < -5 || delta > 45)
                continue;
            String workerPool = node.path("pool").asText("all");
            if (!(workerPool.equals(pool) || "all".equals(workerPool)))
                continue;
            if (!node.path("accepting_jobs").asBoolean(false) || node.path("draining").asBoolean(false))
                continue;
            return true;
        }
        return false;
    }

    /** 统计心跳时间在新鲜阈值内的 worker 数量；Redis 异常时保守返回 0。 */
    private long freshWorkerCount() {
        try {
            long now = Instant.now().getEpochSecond();
            Set<String> keys = redis.keys("judge:worker:*");
            if (keys == null)
                return 0;
            long fresh = 0;
            for (String key : keys) {
                String value = redis.opsForValue().get(key);
                if (value == null)
                    continue;
                // 以心跳时间戳判断存活，避免依赖会残留的注册键
                var node = mapper.readTree(value);
                long stamp = node.path("heartbeat_unix").asLong(0);
                if (now - stamp <= workerFreshSeconds)
                    fresh++;
            }
            return fresh;
        } catch (Exception exception) {
            return 0;
        }
    }

    /** 构造统一健康响应体，附带请求 id 以便与链路日志关联。 */
    private Map<String, Object> body(String status, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("request_id", request.getAttribute(RequestIdConstants.ATTRIBUTE));
        return body;
    }
}
