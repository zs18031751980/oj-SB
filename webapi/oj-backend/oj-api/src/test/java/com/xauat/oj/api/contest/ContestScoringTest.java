package com.xauat.oj.api.contest;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACM 罚时 / OI 部分分 / 重复提交一致性测试。
 */
class ContestScoringTest {

    private ContestScoring.ProblemScore score() {
        return new ContestScoring.ProblemScore("A");
    }

    @Test
    void acmPenaltyCountsWrongAttemptsBeforeFirstAccepted() {
        var p = score();
        p.accept("WA", "WA", 0, 100, 0, 20, true, 20);
        p.accept("AC", "AC", 100, 100, 0, 25, true, 20);

        assertEquals(1, p.solvedCount());
        // 25（AC 用时）+ 1 次错误 × 20 罚时 = 45
        assertEquals(45, p.penalty());
        assertEquals(100, p.oiScore());
    }

    @Test
    void submissionsAfterAcceptedDoNotChangeScore() {
        var p = score();
        p.accept("AC", "AC", 100, 100, 0, 10, true, 20);
        p.accept("WA", "WA", 0, 100, 0, 30, true, 20);

        assertEquals(1, p.solvedCount());
        assertEquals(10, p.penalty());
        assertEquals(100, p.oiScore(), "AC 后不应因后续错误回退成绩");
    }

    @Test
    void unsolvedProblemHasNoPenaltyAndNoSolvedCount() {
        var p = score();
        p.accept("WA", "WA", 0, 100, 0, 5, true, 20);

        assertEquals(0, p.solvedCount());
        assertEquals(0, p.penalty());
        assertEquals(0, p.oiScore());
    }

    @Test
    void oiKeepsBestPartialScoreAndNoPenalty() {
        var p = score();
        p.accept("Partial", "Partial", 30, 100, 0, 10, false, 20);
        p.accept("Partial", "Partial", 80, 100, 0, 20, false, 20);
        p.accept("AC", "AC", 100, 100, 0, 30, false, 20);

        assertEquals(1, p.solvedCount());
        assertEquals(0, p.penalty());
        assertEquals(100, p.oiScore());
    }

    @Test
    void oiExplicitScoreOnlyImproves() {
        var p = score();
        p.accept("Partial", "Partial", 0, 100, 60, 10, false, 20);
        p.accept("Partial", "Partial", 0, 100, 50, 20, false, 20);

        assertEquals(60, p.oiScore(), "OI 取历史最高分，不能被较低分覆盖");
    }

    @Test
    void duplicateAcceptedDoesNotAddPenalty() {
        var p = score();
        p.accept("AC", "AC", 100, 100, 0, 10, true, 20);
        p.accept("AC", "AC", 100, 100, 0, 20, true, 20);

        assertEquals(1, p.solvedCount());
        assertEquals(10, p.penalty());
    }

    @Test
    void zeroTotalDoesNotDivideByZero() {
        var p = score();
        p.accept("WA", "WA", 0, 0, 0, 1, true, 20);
        assertEquals(0, p.oiScore());
    }

    @Test
    void minutesBetweenHandlesNulls() {
        assertEquals(0, ContestScoring.minutesBetween(null, LocalDateTime.now()));
        assertEquals(0, ContestScoring.minutesBetween(LocalDateTime.now(), null));
        var start = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertEquals(25, ContestScoring.minutesBetween(start, start.plusMinutes(25)));
    }

    @Test
    void viewExposesExpectedFields() {
        var p = score();
        p.accept("AC", "AC", 100, 100, 0, 10, false, 20);
        var view = p.asMap();
        assertEquals("A", view.get("problem_index"));
        assertEquals(true, view.get("solved"));
        assertEquals(1, view.get("submissions"));
        assertEquals(100, view.get("score"));
    }
}
