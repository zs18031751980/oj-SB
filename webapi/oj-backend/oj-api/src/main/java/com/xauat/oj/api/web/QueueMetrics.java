package com.xauat.oj.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 判题队列、outbox、worker 与依赖健康指标，供 Prometheus 采集。 */
@Component
public class QueueMetrics {
    /**
     * 注册各类指标；所有指标以弱引用/惰性回调方式采集，避免启动时即访问依赖。
     */
    public QueueMetrics(MeterRegistry registry, JudgeQueue queue,
            SubmissionOutboxRepository submissionOutbox,
            ContestJudgeOutboxRepository contestOutbox,
            ReferenceValidationJobRepository validationJobs,
            StringRedisTemplate redis, ObjectMapper mapper, DataSource dataSource) {
        // 每个逻辑队列注册大小/在途/死信/重试四个 gauge，标签仅含 queue 以保持低基数
        for (String name : List.of(JudgeQueues.REGULAR, JudgeQueues.CONTEST, JudgeQueues.PRACTICE, JudgeQueues.REJUDGE,
                JudgeQueues.TESTCASE_GEN)) {
            registry.gauge("oj.judge.queue.size", List.of(Tag.of("queue", name)), queue, q -> q.size(name));
            registry.gauge("oj.judge.queue.inflight", List.of(Tag.of("queue", name)), queue,
                    q -> q.size(name + ":inflight"));
            registry.gauge("oj.judge.queue.dead", List.of(Tag.of("queue", name)), queue,
                    q -> q.size(name + ":dead-letter"));
            registry.gauge("oj.judge.queue.retry", List.of(Tag.of("queue", name)), queue, q -> q.retrySize(name));
        }
        // outbox 积压量与最老待处理时长是判题延迟的关键信号
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.REGULAR)), submissionOutbox,
                r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.CONTEST)), contestOutbox,
                r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.TESTCASE_GEN)), validationJobs,
                r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.REGULAR)), submissionOutbox,
                r -> ageSeconds(r.oldestPending()));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.CONTEST)), contestOutbox,
                r -> ageSeconds(r.oldestPending()));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.TESTCASE_GEN)), validationJobs,
                r -> ageSeconds(r.oldestPending()));
        // worker 存活数与依赖连通性指标
        registry.gauge("oj.judge.workers.alive", redis, r -> (double) workerAlive(r, mapper));
        registry.gauge("oj.dependency.up", List.of(Tag.of("dependency", "redis")), redis, QueueMetrics::redisUp);
        registry.gauge("oj.dependency.up", List.of(Tag.of("dependency", "postgres")), dataSource, QueueMetrics::dbUp);
    }

    /** 计算最老待处理的秒数；为空返回 0，负值截断为 0。 */
    private static double ageSeconds(LocalDateTime oldest) {
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, LocalDateTime.now()).getSeconds());
    }

    /** 统计心跳时间在 45 秒内的 worker 数量；异常时返回 0。 */
    private static int workerAlive(StringRedisTemplate redis, ObjectMapper mapper) {
        try {
            long now = Instant.now().getEpochSecond();
            Set<String> keys = redis.keys("judge:worker:*");
            if (keys == null)
                return 0;
            int alive = 0;
            for (String key : keys) {
                String value = redis.opsForValue().get(key);
                if (value == null)
                    continue;
                // 以心跳时间戳判定存活，避免依赖可能残留的键存在性
                long stamp = mapper.readTree(value).path("heartbeat_unix").asLong(0);
                if (now - stamp <= 45)
                    alive++;
            }
            return alive;
        } catch (Exception exception) {
            return 0;
        }
    }

    /** Redis ping 成功返回 1，否则 0。 */
    private static double redisUp(StringRedisTemplate redis) {
        try {
            return redis.getConnectionFactory().getConnection().ping() == null ? 0 : 1;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /** 数据库连接可用返回 1，否则 0。 */
    private static double dbUp(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? 1 : 0;
        } catch (Exception exception) {
            return 0;
        }
    }
}
