package com.xauat.oj.api.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeResultViewTest {
    private final JudgeResultView view = new JudgeResultView(new ObjectMapper());

    @Test
    void parsesJudge0RawResults() {
        String payload = "[{\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\"42\",\"stdin\":\"1\",\"expected_output\":\"42\"},"
                + "{\"passed\":false,\"stdout\":\"1\",\"input\":\"2\",\"expected\":\"3\"}]";
        List<Map<String, Object>> parsed = view.parse(payload);
        assertEquals(2, parsed.size());
        assertTrue((Boolean) parsed.get(0).get("passed"));
        assertEquals("42", parsed.get(0).get("actualOutput"));
        assertFalse((Boolean) parsed.get(1).get("passed"));
        assertEquals(1, view.failIndex(parsed));
    }

    @Test
    void recognizesCompilationErrors() {
        assertEquals("编译失败，请检查代码", view.compileError("CE"));
        assertEquals("编译失败，请检查代码", view.compileError("Compilation Error"));
        assertEquals(null, view.compileError("WA"));
    }
}
