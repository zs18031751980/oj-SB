package com.xauat.oj.infrastructure.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本机执行器安全开关测试：未显式开启或生产环境时一律拒绝执行。
 */
class LocalCodeRunnerTest {

    @Test
    void disabledWhenNotExplicitlyEnabled() {
        var result = new LocalCodeRunner(false, "development").run("print(1)", "python", "", 3);
        assertEquals("本机代码执行已禁用", result.message());
        assertNull(result.exitCode());
    }

    @Test
    void disabledInProductionEvenIfEnabled() {
        var result = new LocalCodeRunner(true, "production").run("print(1)", "python", "", 3);
        assertEquals("本机代码执行已禁用", result.message());
        assertNull(result.exitCode());
    }
}
