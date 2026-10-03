package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.domain.Contest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContestScoreboardKindTest {
    private final ContestScoreboardService service = new ContestScoreboardService(null, null, null, null, null, null, null);

    @Test
    void draftAndRunningUseLive() {
        Contest contest = Contest.create("t", "d", "ACM", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 1);
        assertEquals(ContestScoreboardService.LIVE, service.kindFor(contest, false));
    }

    @Test
    void frozenContestHidesFromPublicButNotJury() {
        Contest contest = Contest.create("t", "d", "ACM", LocalDateTime.now().minusHours(2), LocalDateTime.now().plusHours(1), 1);
        contest.updateRules(null, null, null, null, LocalDateTime.now().minusMinutes(5));
        assertEquals(ContestScoreboardService.PUBLIC_FREEZE, service.kindFor(contest, false));
        assertEquals(ContestScoreboardService.LIVE, service.kindFor(contest, true));
    }

    @Test
    void finalizedIsFinalSnapshot() {
        Contest contest = Contest.create("t", "d", "ACM", LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), 1);
        contest.finalizeContest();
        assertTrue(contest.getLifecycleState().equals("FINALIZED"));
        assertEquals(ContestScoreboardService.FINAL, service.kindFor(contest, false));
    }
}
