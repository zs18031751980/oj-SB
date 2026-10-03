package com.xauat.oj.infrastructure.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputCheckerTest {
    @Test
    void textAndExactIgnoreTrailingWhitespace() {
        assertTrue(OutputChecker.matches("exact", "42\n", "42"));
        assertTrue(OutputChecker.matches("text", "  42  ", "42"));
        assertFalse(OutputChecker.matches("exact", "42 43", "42"));
    }

    @Test
    void tokensCompareWhitespaceInsensitively() {
        assertTrue(OutputChecker.matches("tokens", "1  2\t3", "1 2 3"));
        assertFalse(OutputChecker.matches("tokens", "1 2", "1 2 3"));
    }

    @Test
    void floatAllowsTolerance() {
        assertTrue(OutputChecker.matches("float", "1.0000001 2.5", "1.0 2.5000002"));
        assertFalse(OutputChecker.matches("float", "1.5", "1.0"));
    }

    @Test
    void customIsUnsupportedByLocalChecker() {
        assertFalse(OutputChecker.supported("custom"));
        assertTrue(OutputChecker.supported("tokens"));
        assertTrue(Checker.supportedName("custom"));
        assertTrue(Checker.supportedName("float"));
        assertFalse(Checker.supportedName("bogus"));
    }

    @Test
    void floatHonorsConfiguredTolerance() {
        assertTrue(OutputChecker.matches("float", "1.0005", "1.0", 0.001, 0.0));
        assertFalse(OutputChecker.matches("float", "1.5", "1.0", 0.001, 0.0));
    }
}
