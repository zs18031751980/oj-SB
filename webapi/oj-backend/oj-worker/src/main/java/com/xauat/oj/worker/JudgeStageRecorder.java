package com.xauat.oj.worker;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 低基数判题阶段直方图：写入 Redis hash，供 API 的 /metrics 导出。 */
@Component
public class JudgeStageRecorder {
    private static final double[] BUCKETS = {0.01, 0.05, 0.1, 0.5, 1, 2, 5, 10, 30, 60, 300};

    private final StringRedisTemplate redis;

    public JudgeStageRecorder(StringRedisTemplate redis) { this.redis = redis; }

    public void observe(String pool, String stage, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return;
        try {
            String prefix = pool + ":" + stage + ":";
            redis.opsForHash().increment("judge:stage:histogram", prefix + "count", 1);
            redis.opsForHash().increment("judge:stage:histogram", prefix + "sum", seconds);
            for (double bound : BUCKETS) {
                if (seconds <= bound) redis.opsForHash().increment("judge:stage:histogram", prefix + bound, 1);
            }
        } catch (RuntimeException ignored) { }
    }
}
