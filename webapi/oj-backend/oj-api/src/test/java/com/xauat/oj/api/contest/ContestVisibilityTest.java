package com.xauat.oj.api.contest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 比赛可见性策略测试：草稿/就绪/取消、私有比赛与参赛者/管理权限的边界。
 */
class ContestVisibilityTest {

    @Test
    void draftReadyCancelledHiddenFromAnonymous() {
        assertFalse(ContestVisibility.canView("DRAFT", true, false, false, false));
        assertFalse(ContestVisibility.canView("READY", true, false, false, false));
        assertFalse(ContestVisibility.canView("CANCELLED", true, false, false, false));
    }

    @Test
    void publishedPublicContestVisible() {
        assertTrue(ContestVisibility.canView("SCHEDULED", true, false, false, false));
        assertTrue(ContestVisibility.canView("RUNNING", true, false, false, false));
        assertTrue(ContestVisibility.canView("FINALIZED", true, false, false, false));
    }

    @Test
    void privateContestVisibleOnlyToParticipant() {
        assertFalse(ContestVisibility.canView("RUNNING", false, false, false, false));
        assertTrue(ContestVisibility.canView("RUNNING", false, false, false, true));
    }

    @Test
    void privilegedAndJurySeeDraftAndPrivate() {
        assertTrue(ContestVisibility.canView("DRAFT", false, true, false, false));
        assertTrue(ContestVisibility.canView("DRAFT", false, false, true, false));
    }
}
