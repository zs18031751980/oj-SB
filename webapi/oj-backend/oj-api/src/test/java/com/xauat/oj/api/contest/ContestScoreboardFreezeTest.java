package com.xauat.oj.api.contest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.repository.ContestParticipantRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestScoreboardSnapshotRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 封榜/解封与榜单可见性测试：封榜后非裁判只能看到 PUBLIC_FREEZE，解封后恢复 LIVE。
 */
class ContestScoreboardFreezeTest {

    private ContestScoreboardService service() {
        return new ContestScoreboardService(mock(ContestProblemRepository.class),
                mock(ContestSubmissionRepository.class), mock(ContestParticipantRepository.class),
                mock(ContestScoreboardSnapshotRepository.class), mock(UserRepository.class), new ObjectMapper(),
                mock(EntryResolver.class));
    }

    private Contest contest(String lifecycle, LocalDateTime freezeTime, LocalDateTime thawedAt) {
        Contest contest = mock(Contest.class);
        when(contest.getLifecycleState()).thenReturn(lifecycle);
        when(contest.getFreezeTime()).thenReturn(freezeTime);
        when(contest.getThawedAt()).thenReturn(thawedAt);
        return contest;
    }

    @Test
    void notFrozenBeforeFreezeTime() {
        assertFalse(service().isFrozen(contest("RUNNING", LocalDateTime.now().plusMinutes(10), null)));
    }

    @Test
    void frozenAfterFreezeTimeUntilThawed() {
        var frozen = contest("RUNNING", LocalDateTime.now().minusMinutes(1), null);
        assertTrue(service().isFrozen(frozen));
        // 解封后不再封榜
        assertFalse(service().isFrozen(contest("RUNNING", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now())));
    }

    @Test
    void draftReadyCancelledNeverFrozen() {
        var past = LocalDateTime.now().minusMinutes(1);
        assertFalse(service().isFrozen(contest("DRAFT", past, null)));
        assertFalse(service().isFrozen(contest("READY", past, null)));
        assertFalse(service().isFrozen(contest("CANCELLED", past, null)));
    }

    @Test
    void kindSelectionHonorsFreezeAndFinalRevision() {
        var svc = service();
        var frozen = contest("RUNNING", LocalDateTime.now().minusMinutes(1), null);
        assertEquals(ContestScoreboardService.PUBLIC_FREEZE, svc.kindFor(frozen, false));
        // 裁判可看实时榜
        assertEquals(ContestScoreboardService.LIVE, svc.kindFor(frozen, true));

        Contest finalized = mock(Contest.class);
        when(finalized.getLifecycleState()).thenReturn("FINALIZED");
        when(finalized.getFinalRevision()).thenReturn(3);
        assertEquals("FINAL:3", svc.kindFor(finalized, false));
    }
}
