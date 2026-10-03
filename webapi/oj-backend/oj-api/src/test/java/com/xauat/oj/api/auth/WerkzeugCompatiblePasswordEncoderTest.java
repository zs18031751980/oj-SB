package com.xauat.oj.api.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WerkzeugCompatiblePasswordEncoderTest {
    private final WerkzeugCompatiblePasswordEncoder encoder = new WerkzeugCompatiblePasswordEncoder();
    private static final String PASSWORD = "S3cret!密码";

    // 由 Werkzeug 3.1.6 等价算法离线生成，用于验证旧密码哈希兼容性。
    private static final String SCRYPT =
            "scrypt:32768:8:1$abcXYZ1234567890$f69888a8df7b6f9fdecd259acf6bd743f9f9a9e234cde8d7df3573e000dbe760b399b8d968c4bf541fc677502db165632b34e437c229fe78ae2087ac43ff0d49";
    private static final String PBKDF2_SHA256 =
            "pbkdf2:sha256:600000$abcXYZ1234567890$dd7e10b91c87644b525f5b7cb73d4c19c3b2812ec11ef6527a449c01d64048a6";
    private static final String PBKDF2_SHA1 =
            "pbkdf2:sha1:1000$abcXYZ1234567890$2190a95ef7cb7c0096b581d9e3646051422d6e9e";

    @Test
    void verifiesWerkzeugScryptHash() {
        assertTrue(encoder.matches(PASSWORD, SCRYPT));
        assertFalse(encoder.matches("wrong-password", SCRYPT));
    }

    @Test
    void verifiesWerkzeugPbkdf2Sha256Hash() {
        assertTrue(encoder.matches(PASSWORD, PBKDF2_SHA256));
    }

    @Test
    void verifiesWerkzeugPbkdf2Sha1Hash() {
        assertTrue(encoder.matches(PASSWORD, PBKDF2_SHA1));
    }

    @Test
    void newHashesAreBcryptAndDoNotNeedUpgrade() {
        String hash = encoder.encode(PASSWORD);
        assertTrue(encoder.matches(PASSWORD, hash));
        assertFalse(encoder.upgradeEncoding(hash));
    }

    @Test
    void legacyHashesReportedForUpgrade() {
        assertTrue(encoder.upgradeEncoding(SCRYPT));
        assertTrue(encoder.upgradeEncoding(PBKDF2_SHA256));
    }
}
