package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 比赛判题 outbox 公平调度排序单元测试：验证按 contest+entry 分组并轮转交错。
 */
class ContestOutboxDispatcherFairnessTest {

    private static ContestJudgeOutbox outbox(Integer contestId, Integer teamId, Integer userId) {
        ContestSubmission submission = mock(ContestSubmission.class);
        when(submission.getContestId()).thenReturn(contestId);
        when(submission.getTeamId()).thenReturn(teamId);
        when(submission.getUserId()).thenReturn(userId);
        ContestJudgeOutbox outbox = mock(ContestJudgeOutbox.class);
        when(outbox.getSubmission()).thenReturn(submission);
        return outbox;
    }

    @Test
    void fairnessKeyDistinguishesContestAndEntry() {
        assertEquals("1:u7", ContestOutboxDispatcher.fairnessKey(outbox(1, null, 7)));
        assertEquals("1:t9", ContestOutboxDispatcher.fairnessKey(outbox(1, 9, 7)));
        assertNotEquals(ContestOutboxDispatcher.fairnessKey(outbox(1, null, 7)),
                ContestOutboxDispatcher.fairnessKey(outbox(2, null, 7)));
    }

    @Test
    void fairOrderInterleavesEntriesRoundRobin() {
        var a1 = outbox(1, null, 1);
        var a2 = outbox(1, null, 1);
        var b1 = outbox(2, null, 2);
        var ordered = ContestOutboxDispatcher.fairOrder(List.of(a1, a2, b1));
        // 同一比赛连续两条会被另一比赛的不同 entry 隔开，避免单场独占队首
        assertSame(a1, ordered.get(0));
        assertSame(b1, ordered.get(1));
        assertSame(a2, ordered.get(2));
    }

    @Test
    void fairOrderHandlesEmptyAndSingle() {
        assertTrue(ContestOutboxDispatcher.fairOrder(List.of()).isEmpty());
        var single = outbox(1, null, 1);
        assertEquals(List.of(single), ContestOutboxDispatcher.fairOrder(List.of(single)));
    }
}
