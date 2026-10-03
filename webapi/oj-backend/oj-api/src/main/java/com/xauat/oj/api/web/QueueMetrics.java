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
    public QueueMetrics(MeterRegistry registry, JudgeQueue queue,
                        SubmissionOutboxRepository submissionOutbox,
                        ContestJudgeOutboxRepository contestOutbox,
                        ReferenceValidationJobRepository validationJobs,
                        StringRedisTemplate redis, ObjectMapper mapper, DataSource dataSource) {
        for (String name : List.of(JudgeQueues.REGULAR, JudgeQueues.CONTEST, JudgeQueues.PRACTICE, JudgeQueues.REJUDGE, JudgeQueues.TESTCASE_GEN)) {
            registry.gauge("oj.judge.queue.size", List.of(Tag.of("queue", name)), queue, q -> q.size(name));
            registry.gauge("oj.judge.queue.inflight", List.of(Tag.of("queue", name)), queue, q -> q.size(name + ":inflight"));
            registry.gauge("oj.judge.queue.dead", List.of(Tag.of("queue", name)), queue, q -> q.size(name + ":dead-letter"));
            registry.gauge("oj.judge.queue.retry", List.of(Tag.of("queue", name)), queue, q -> q.retrySize(name));
        }
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.REGULAR)), submissionOutbox, r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.CONTEST)), contestOutbox, r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.pending", List.of(Tag.of("queue", JudgeQueues.TESTCASE_GEN)), validationJobs, r -> r.countByState("PENDING"));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.REGULAR)), submissionOutbox, r -> ageSeconds(r.oldestPending()));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.CONTEST)), contestOutbox, r -> ageSeconds(r.oldestPending()));
        registry.gauge("oj.outbox.oldest.seconds", List.of(Tag.of("queue", JudgeQueues.TESTCASE_GEN)), validationJobs, r -> ageSeconds(r.oldestPending()));
        registry.gauge("oj.judge.workers.alive", redis, r -> (double) workerAlive(r, mapper));
        registry.gauge("oj.dependency.up", List.of(Tag.of("dependency", "redis")), redis, QueueMetrics::redisUp);
        registry.gauge("oj.dependency.up", List.of(Tag.of("dependency", "postgres")), dataSource, QueueMetrics::dbUp);
    }

    private static double ageSeconds(LocalDateTime oldest) {
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, LocalDateTime.now()).getSeconds());
    }

    private static int workerAlive(StringRedisTemplate redis, ObjectMapper mapper) {
        try {
            long now = Instant.now().getEpochSecond();
            Set<String> keys = redis.keys("judge:worker:*");
            if (keys == null) return 0;
            int alive = 0;
            for (String key : keys) {
                String value = redis.opsForValue().get(key);
                if (value == null) continue;
                long stamp = mapper.readTree(value).path("heartbeat_unix").asLong(0);
                if (now - stamp <= 45) alive++;
            }
            return alive;
        } catch (Exception exception) {
            return 0;
        }
    }

    private static double redisUp(StringRedisTemplate redis) {
        try { return redis.getConnectionFactory().getConnection().ping() == null ? 0 : 1; }
        catch (RuntimeException exception) { return 0; }
    }

    private static double dbUp(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) { return connection.isValid(2) ? 1 : 0; }
        catch (Exception exception) { return 0; }
    }
}
