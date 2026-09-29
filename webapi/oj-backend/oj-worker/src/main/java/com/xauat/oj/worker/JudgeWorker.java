package com.xauat.oj.worker;

import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import com.xauat.oj.core.contest.repository.JudgeDeadLetterRepository;

/**
 * Worker 只负责领取和编排任务；真正的 Docker/Judge0 执行器后续通过 JudgeExecutor 注入。
 * 领取和执行之间不持有数据库事务，避免长时间占用 PostgreSQL 连接。
 */
@Component
public class JudgeWorker {
    private final JudgeQueue queue;
    private final JudgeExecutor executor;
    private final JudgeDeadLetterRepository deadLetters;
    private final String workerId = UUID.randomUUID().toString();
    private final ScheduledExecutorService leaseScheduler = Executors.newScheduledThreadPool(1);

    public JudgeWorker(JudgeQueue queue, JudgeExecutor executor, JudgeDeadLetterRepository deadLetters) {
        this.queue = queue;
        this.executor = executor;
        this.deadLetters = deadLetters;
    }

    @Scheduled(fixedDelayString = "${oj.worker.poll-delay-ms:1000}")
    public void poll() {
        pollQueue("oj:judge:queue");
        pollQueue("oj:contest:judge:queue");
    }

    @Scheduled(fixedDelayString = "${oj.worker.recovery-delay-ms:30000}")
    public void recoverExpired() {
        queue.recoverExpired("oj:judge:queue", 100);
        queue.recoverExpired("oj:contest:judge:queue", 100);
        queue.recoverRetries("oj:judge:queue", 100);
        queue.recoverRetries("oj:contest:judge:queue", 100);
    }

    private void pollQueue(String queueName) {
        var claim = queue.claim(queueName, workerId, 60);
        if (claim == null) return;
        String jobId = claim.jobId();
        ScheduledFuture<?> leaseRenewal = leaseScheduler.scheduleAtFixedRate(() -> queue.renew(jobId, claim.receipt(), 60), 20, 20, TimeUnit.SECONDS);
        try {
            executor.execute(jobId);
            // 只有结果已由执行器持久化后才 ACK，失败必须保留给重试/死信流程。
            queue.acknowledge(queueName, jobId, claim.receipt());
        } catch (RuntimeException exception) {
            long attempts = queue.reject(queueName, jobId, exception.getClass().getSimpleName());
            if (attempts > 3) deadLetters.save(JudgeDeadLetter.create(queueName, jobId, exception.getClass().getSimpleName()));
        } finally {
            leaseRenewal.cancel(false);
        }
    }
}
