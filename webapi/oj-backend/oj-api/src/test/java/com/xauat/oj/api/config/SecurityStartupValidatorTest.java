package com.xauat.oj.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启动期安全校验测试：占位密钥、生产本机执行、指标令牌缺失等必须拒绝启动。
 */
class SecurityStartupValidatorTest {
    private static final String GOOD_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String GOOD_METRICS = "metrics-token-0123456789";

    private SecurityStartupValidator validator(String env, String executor, String jwt, String metrics,
            boolean local, boolean allowAnonymous) {
        return new SecurityStartupValidator(env, executor, jwt, metrics, local, allowAnonymous, true);
    }

    @Test
    void developmentWithStrongSecretsPasses() {
        assertDoesNotThrow(() -> validator("development", "judge0", GOOD_SECRET, GOOD_METRICS, false, false)
                .validate());
    }

    @Test
    void placeholderJwtIsRejectedInAllEnvironments() {
        assertThrows(IllegalStateException.class, () -> validator("development", "judge0",
                "change-this-secret-in-production-change-this-secret-in-production", GOOD_METRICS, false, false)
                .validate());
    }

    @Test
    void shortJwtIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> validator("development", "judge0", "short", GOOD_METRICS, false, false).validate());
    }

    @Test
    void placeholderMetricsTokenIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> validator("development", "judge0", GOOD_SECRET, "change-me-metrics-token", false, false)
                        .validate());
    }

    @Test
    void productionForbidsLocalExecutor() {
        assertThrows(IllegalStateException.class,
                () -> validator("production", "local", GOOD_SECRET, GOOD_METRICS, true, false).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("production", "local", GOOD_SECRET, GOOD_METRICS, false, false).validate());
    }

    @Test
    void productionRequiresMetricsTokenOrExplicitAnonymous() {
        assertThrows(IllegalStateException.class,
                () -> validator("production", "judge0", GOOD_SECRET, "", false, false).validate());
        assertDoesNotThrow(
                () -> validator("production", "judge0", GOOD_SECRET, "", false, true).validate());
    }

    @Test
    void productionRequiresSecureCookie() {
        assertThrows(IllegalStateException.class, () -> new SecurityStartupValidator("production", "judge0",
                GOOD_SECRET, GOOD_METRICS, false, false, false).validate());
    }
}
