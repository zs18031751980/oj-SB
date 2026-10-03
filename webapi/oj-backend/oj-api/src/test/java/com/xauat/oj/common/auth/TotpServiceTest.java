package com.xauat.oj.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {
    private final TotpService totp = new TotpService();
    // RFC 6238 SHA1 测试密钥 ASCII "12345678901234567890" 的 Base32。
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238Vector() {
        // 时间 59s → 计数器 1 → 8 位 94287082，取模后 6 位 287082。
        assertEquals(1L, totp.match(SECRET, "287082", 59).orElseThrow());
    }

    @Test
    void acceptsPreviousTimeStepWithinWindow() {
        // 计数器 1 的验证码在时间 89s（计数器 2）时仍应被 ±1 窗口接受。
        assertTrue(totp.match(SECRET, "287082", 30 * 2 + 5).isPresent());
    }

    @Test
    void rejectsWrongOrMalformedCode() {
        assertFalse(totp.match(SECRET, "000000", 59).isPresent());
        assertFalse(totp.match(SECRET, "abc123", 59).isPresent());
        assertFalse(totp.match("", "287082", 59).isPresent());
    }
}
