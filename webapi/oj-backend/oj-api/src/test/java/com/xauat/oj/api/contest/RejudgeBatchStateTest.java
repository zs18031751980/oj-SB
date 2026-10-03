package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.RejudgeBatch;
import com.xauat.oj.core.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejudgeBatchStateTest {
    private RejudgeBatch batch() {
        Contest contest = Contest.create("t", "d", "ACM", LocalDateTime.now(), LocalDateTime.now().plusHours(1), 1);
        User actor = User.local("jury", "jury@example.com", "hash");
        return RejudgeBatch.create(contest, actor, "reason");
    }

    @Test
    void startsPendingAndCanBeApplied() {
        RejudgeBatch batch = batch();
        assertTrue(batch.isPending());
        assertEquals("PENDING", batch.getState());
        batch.apply(9);
        assertEquals("APPLIED", batch.getState());
        assertFalse(batch.isPending());
        assertEquals("reason", batch.getReason());
    }

    @Test
    void canBeCancelled() {
        RejudgeBatch batch = batch();
        batch.cancel(9);
        assertEquals("CANCELLED", batch.getState());
    }
}
