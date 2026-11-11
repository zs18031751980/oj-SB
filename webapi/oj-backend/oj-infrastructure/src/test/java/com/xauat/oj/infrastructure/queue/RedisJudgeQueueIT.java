package com.xauat.oj.infrastructure.queue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Redis 下的队列可靠性集成测试（需 Docker，无 Docker 时自动跳过）。
 *
 * <p>覆盖：多消费者并发抢占时同一任务只被领取一次；租约过期后任务可被恢复。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisJudgeQueueIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisJudgeQueue queue;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        queue = new RedisJudgeQueue(template);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null)
            connectionFactory.destroy();
    }

    @Test
    void concurrentWorkersClaimEachJobExactlyOnce() throws Exception {
        String topic = "it:claim:" + UUID.randomUUID();
        int jobs = 60;
        for (int i = 0; i < jobs; i++)
            queue.publish(topic, "job-" + i);

        Set<String> claimed = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            String workerId = "worker-" + w;
            futures.add(workers.submit(() -> {
                while (true) {
                    QueueClaim claim = queue.claim(topic, workerId, 30);
                    if (claim == null)
                        return;
                    if (!claimed.add(claim.jobId()))
                        duplicates.incrementAndGet();
                    queue.acknowledge(topic, claim.jobId(), claim.receipt());
                }
            }));
        }
        for (Future<?> future : futures)
            future.get(60, TimeUnit.SECONDS);
        workers.shutdownNow();

        assertEquals(jobs, claimed.size(), "每个任务都应且只被领取一次");
        assertEquals(0, duplicates.get(), "不应出现重复领取");
    }

    @Test
    void expiredLeaseIsRecoveredAndReclaimable() throws Exception {
        String topic = "it:recover:" + UUID.randomUUID();
        queue.publish(topic, "job-r");

        QueueClaim first = queue.claim(topic, "worker-a", 1);
        assertNotNull(first);
        assertEquals("job-r", first.jobId());

        // 等待 1 秒租约过期，恢复后应重新入队并可被其它 Worker 领取
        Thread.sleep(1500);
        assertEquals(1, queue.recoverExpired(topic, 10));

        QueueClaim second = queue.claim(topic, "worker-b", 30);
        assertNotNull(second, "租约过期任务必须可被恢复领取");
        assertEquals("job-r", second.jobId());
        queue.acknowledge(topic, second.jobId(), second.receipt());
    }
}
