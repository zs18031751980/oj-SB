package com.xauat.oj.infrastructure.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeVerdictTest {
    @Test
    void mapsJudge0StatusesToCanonicalCodes() {
        assertEquals("AC", JudgeVerdict.fromStatus(3, "Accepted"));
        assertEquals("WA", JudgeVerdict.fromStatus(4, "Wrong Answer"));
        assertEquals("TLE", JudgeVerdict.fromStatus(5, "Time Limit Exceeded"));
        assertEquals("CE", JudgeVerdict.fromStatus(6, "Compilation Error"));
        assertEquals("SIGSEGV", JudgeVerdict.fromStatus(7, "Runtime Error"));
        assertEquals("RE", JudgeVerdict.fromStatus(11, "Runtime Error"));
        assertEquals("SystemError", JudgeVerdict.fromStatus(13, "Internal Error"));
    }

    @Test
    void terminalClassification() {
        assertTrue(JudgeVerdict.terminal("AC"));
        assertTrue(JudgeVerdict.terminal("Partial"));
        assertFalse(JudgeVerdict.terminal("Pending"));
        assertFalse(JudgeVerdict.terminal("Judging"));
    }
}
