package com.xauat.oj.worker;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import com.xauat.oj.core.contest.repository.JudgeDeadLetterRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Worker 按 {@code oj.worker.pool} 领取对应队列；关闭时先 drain（停止接单）再退出。
 * 领取和执行之间不持有数据库事务，避免长时间占用 PostgreSQL 连接。
 */
@Component
public class JudgeWorker {
    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final JudgeQueue queue;
    private final JudgeExecutor executor;
    private final ValidationExecutor validationExecutor;
    private final JudgeDeadLetterRepository deadLetters;
    private final WorkerHeartbeat heartbeat;
    private final List<String> judgeQueues;
    private final boolean handlesValidation;
    private final String workerId = UUID.randomUUID().toString();
    private final ScheduledExecutorService leaseScheduler = Executors.newScheduledThreadPool(1);

    public JudgeWorker(JudgeQueue queue, JudgeExecutor executor, ValidationExecutor validationExecutor,
                       JudgeDeadLetterRepository deadLetters, WorkerHeartbeat heartbeat,
                       @Value("${oj.worker.pool:all}") String pool) {
        this.queue = queue; this.executor = executor; this.validationExecutor = validationExecutor;
        this.deadLetters = deadLetters; this.heartbeat = heartbeat;
        List<String> queues = new ArrayList<>();
        boolean validation = false;
        switch (pool == null ? "all" : pool.toLowerCase(java.util.Locale.ROOT)) {
            case "contest" -> queues.add(JudgeQueues.CONTEST);
            case "practice" -> { queues.add(JudgeQueues.REGULAR); queues.add(JudgeQueues.PRACTICE); }
            case "rejudge" -> queues.add(JudgeQueues.REJUDGE);
            case "validation" -> validation = true;
            default -> { queues.add(JudgeQueues.REGULAR); queues.add(JudgeQueues.CONTEST); queues.add(JudgeQueues.PRACTICE); queues.add(JudgeQueues.REJUDGE); validation = true; }
        }
        this.judgeQueues = List.copyOf(queues);
        this.handlesValidation = validation;
    }

    @Scheduled(fixedDelayString = "${oj.worker.poll-delay-ms:1000}")
    public void poll() {
        if (heartbeat.draining()) return;
        for (String queueName : judgeQueues) pollQueue(queueName, executor);
        if (handlesValidation) pollQueue(JudgeQueues.TESTCASE_GEN, validationExecutor);
    }

    @Scheduled(fixedDelayString = "${oj.worker.recovery-delay-ms:30000}")
    public void recoverExpired() {
        for (String queueName : judgeQueues) {
            queue.recoverExpired(queueName, 100);
            queue.recoverRetries(queueName, 100);
        }
        if (handlesValidation) {
            queue.recoverExpired(JudgeQueues.TESTCASE_GEN, 100);
            queue.recoverRetries(JudgeQueues.TESTCASE_GEN, 100);
        }
    }

    @PreDestroy
    public void drain() {
        heartbeat.beginDrain();
        log.info("Worker 进入 drain，停止接单");
        leaseScheduler.shutdown();
    }

    private void pollQueue(String queueName, JudgeExecutor handler) { poll(queueName, handler::execute); }

    private void pollQueue(String queueName, ValidationExecutor handler) { poll(queueName, handler::execute); }

    private void poll(String queueName, java.util.function.Consumer<String> handler) {
        var claim = queue.claim(queueName, workerId, 60);
        if (claim == null) return;
        String jobId = claim.jobId();
        ScheduledFuture<?> leaseRenewal = leaseScheduler.scheduleAtFixedRate(() -> queue.renew(jobId, claim.receipt(), 60), 20, 20, TimeUnit.SECONDS);
        try {
            handler.accept(jobId);
            queue.acknowledge(queueName, jobId, claim.receipt());
        } catch (RuntimeException exception) {
            long attempts = queue.reject(queueName, jobId, exception.getClass().getSimpleName());
            if (attempts > 3) deadLetters.save(JudgeDeadLetter.create(queueName, jobId, exception.getClass().getSimpleName()));
        } finally {
            leaseRenewal.cancel(false);
        }
    }
}
