package com.xauat.oj.worker;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import com.xauat.oj.core.contest.repository.JudgeDeadLetterRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import com.xauat.oj.infrastructure.queue.QueueClaim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Worker 可靠性测试：领取-确认/拒绝、异常进入重试与死信、过期任务恢复。
 *
 * <p>覆盖「Worker 异常退出后任务可被恢复」「失败任务不能被误 ACK」等核心可靠性场景。</p>
 */
class JudgeWorkerReliabilityTest {
    private JudgeQueue queue;
    private JudgeExecutor executor;
    private JudgeDeadLetterRepository deadLetters;
    private WorkerHeartbeat heartbeat;
    private JudgeWorker worker;

    @BeforeEach
    void setUp() {
        queue = mock(JudgeQueue.class);
        executor = mock(JudgeExecutor.class);
        deadLetters = mock(JudgeDeadLetterRepository.class);
        heartbeat = mock(WorkerHeartbeat.class);
        when(heartbeat.draining()).thenReturn(false);
        worker = new JudgeWorker(queue, executor, mock(ValidationExecutor.class), deadLetters, heartbeat, "practice");
    }

    @AfterEach
    void tearDown() {
        // 关闭租约续期线程，避免测试进程挂起
        worker.drain();
    }

    private void claimOneJob() {
        when(queue.claim(eq(JudgeQueues.REGULAR), anyString(), anyLong()))
                .thenReturn(new QueueClaim("job-1", "receipt-1"));
    }

    @Test
    void successfulExecutionAcknowledgesJob() {
        claimOneJob();

        worker.poll();

        verify(executor).execute("job-1");
        verify(queue).acknowledge(JudgeQueues.REGULAR, "job-1", "receipt-1");
        verify(queue, never()).reject(anyString(), anyString(), anyString());
    }

    @Test
    void failedExecutionRejectsAndNeverAcknowledges() {
        claimOneJob();
        doThrow(new RuntimeException("judge backend down")).when(executor).execute("job-1");
        when(queue.reject(eq(JudgeQueues.REGULAR), eq("job-1"), anyString())).thenReturn(1L);

        worker.poll();

        // 失败任务必须进入重试/拒绝路径，不能被确认消费
        verify(queue).reject(eq(JudgeQueues.REGULAR), eq("job-1"), anyString());
        verify(queue, never()).acknowledge(anyString(), anyString(), anyString());
        verify(deadLetters, never()).save(any());
    }

    @Test
    void exceededRetriesGoesToDeadLetter() {
        claimOneJob();
        doThrow(new RuntimeException("permanent")).when(executor).execute("job-1");
        when(queue.reject(eq(JudgeQueues.REGULAR), eq("job-1"), anyString())).thenReturn(4L);

        worker.poll();

        verify(deadLetters).save(any(JudgeDeadLetter.class));
    }

    @Test
    void recoveryReclaimsExpiredAndRetriedTasksForEveryQueue() {
        worker.recoverExpired();

        verify(queue).recoverExpired(JudgeQueues.REGULAR, 100);
        verify(queue).recoverRetries(JudgeQueues.REGULAR, 100);
        verify(queue).recoverExpired(JudgeQueues.PRACTICE, 100);
        verify(queue).recoverRetries(JudgeQueues.PRACTICE, 100);
    }
}
