package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContestOutboxQueueRoutingTest {
    @Test
    void routesToContractQueueNames() {
        assertEquals(JudgeQueues.CONTEST, ContestOutboxDispatcher.queueFor(false, true));
        assertEquals(JudgeQueues.PRACTICE, ContestOutboxDispatcher.queueFor(false, false));
        assertEquals(JudgeQueues.REJUDGE, ContestOutboxDispatcher.queueFor(true, true));
        assertEquals(JudgeQueues.REJUDGE, ContestOutboxDispatcher.queueFor(true, false));
    }
}
