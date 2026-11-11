package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Outbox 派发可靠性测试：派发成功、Redis 不可用时保留 PENDING 并可重试。
 *
 * <p>覆盖「提交已入库但入队失败」的补偿路径：失败记录回到 PENDING，下一轮继续尝试。</p>
 */
class ContestOutboxDispatcherTest {

    private ContestJudgeOutbox outbox(ContestJudgeOutboxRepository outboxes) {
        ContestSubmission submission = mock(ContestSubmission.class);
        when(submission.getJobId()).thenReturn("job-1");
        when(submission.getContestId()).thenReturn(1);
        when(submission.getTeamId()).thenReturn(null);
        when(submission.getUserId()).thenReturn(5);
        when(submission.getRejudgeOfId()).thenReturn(null);
        when(submission.isContestEligible()).thenReturn(true);
        ContestJudgeOutbox outbox = mock(ContestJudgeOutbox.class);
        when(outbox.getSubmission()).thenReturn(submission);
        when(outboxes.claimPending()).thenReturn(List.of(outbox));
        return outbox;
    }

    @Test
    void successfulDispatchMarksDispatched() {
        ContestJudgeOutboxRepository outboxes = mock(ContestJudgeOutboxRepository.class);
        JudgeQueue queue = mock(JudgeQueue.class);
        var outbox = outbox(outboxes);

        new ContestOutboxDispatcher(outboxes, queue).dispatch();

        verify(queue).publishOnce(JudgeQueues.CONTEST, "job-1", 604800);
        verify(outbox).markDispatched();
        verify(outboxes).save(outbox);
    }

    @Test
    void enqueueFailureKeepsPendingAndRetriesNextRound() {
        ContestJudgeOutboxRepository outboxes = mock(ContestJudgeOutboxRepository.class);
        JudgeQueue queue = mock(JudgeQueue.class);
        var outbox = outbox(outboxes);
        when(queue.publishOnce(anyString(), anyString(), anyLong())).thenThrow(new RuntimeException("redis down"));

        var dispatcher = new ContestOutboxDispatcher(outboxes, queue);
        dispatcher.dispatch();
        dispatcher.dispatch();

        // 失败必须记录错误并回到 PENDING（由 markFailed 保证），且下一轮会再次尝试
        verify(outbox, times(2)).markFailed(anyString());
        verify(outbox, never()).markDispatched();
        verify(queue, times(2)).publishOnce(JudgeQueues.CONTEST, "job-1", 604800);
    }
}
