package com.xauat.oj.api.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/** 与旧后端一致的低基数判题阶段直方图端点；需 METRICS_TOKEN（未配置时开发放行）。 */
@RestController
public class MetricsController {
    private static final double[] BUCKETS = {0.01, 0.05, 0.1, 0.5, 1, 2, 5, 10, 30, 60, 300};
    private final StringRedisTemplate redis;
    private final String token;

    public MetricsController(StringRedisTemplate redis, @Value("${oj.metrics.token:}") String token) {
        this.redis = redis; this.token = token == null ? "" : token.trim();
    }

    @GetMapping(value = "/metrics", produces = "text/plain;version=0.0.4;charset=utf-8")
    public ResponseEntity<String> metrics(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!token.isBlank()) {
            String expected = "Bearer " + token;
            if (authorization == null || !MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                return ResponseEntity.status(401).body("{\"error\":\"Unauthorized\"}");
            }
        }
        return ResponseEntity.ok(render());
    }

    private String render() {
        String name = "oj_judge_stage_seconds";
        StringBuilder body = new StringBuilder();
        body.append("# HELP ").append(name).append(" Judge stage duration in seconds\n# TYPE ").append(name).append(" histogram\n");
        try {
            Map<Object, Object> values = redis.opsForHash().entries("judge:stage:histogram");
            for (String pool : List.of("all", "contest", "practice", "rejudge", "validation")) {
                for (String stage : List.of("queue", "compile", "execute", "total", "persist")) {
                    String prefix = pool + ":" + stage + ":";
                    if (!values.containsKey(prefix + "count")) continue;
                    String labels = "pool=\"" + pool + "\",stage=\"" + stage + "\"";
                    for (double bound : BUCKETS) {
                        body.append(name).append("_bucket{").append(labels).append(",le=\"").append(bound).append("\"} ")
                                .append(values.getOrDefault(prefix + bound, "0")).append('\n');
                    }
                    Object count = values.get(prefix + "count");
                    body.append(name).append("_bucket{").append(labels).append(",le=\"+Inf\"} ").append(count).append('\n');
                    body.append(name).append("_count{").append(labels).append("} ").append(count).append('\n');
                    body.append(name).append("_sum{").append(labels).append("} ").append(values.getOrDefault(prefix + "sum", "0")).append('\n');
                }
            }
        } catch (RuntimeException exception) {
            body.append("# redis unavailable\n");
        }
        return body.toString();
    }
}
