package com.xauat.oj.api.judge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将数据库中的判题结果（可能是 Judge0 原始响应或规范化的逐用例明细）转换为
 * 前端 {@code SubmissionResponse.testcase_results} 期望的 camelCase 结构。
 */
@Component
public class JudgeResultView {
    private final ObjectMapper mapper;

    public JudgeResultView(ObjectMapper mapper) { this.mapper = mapper; }

    public List<Map<String, Object>> parse(String payload) {
        if (payload == null || payload.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = mapper.readValue(payload, new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) result.add(item(raw.get(i), i));
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    public Integer failIndex(List<Map<String, Object>> results) {
        for (Map<String, Object> item : results) {
            if (!Boolean.TRUE.equals(item.get("passed"))) return (Integer) item.get("testCaseIndex");
        }
        return null;
    }

    public String compileError(String status) {
        if (status == null) return null;
        String normalized = status.replace(" ", "").toLowerCase();
        if (normalized.equals("ce") || normalized.contains("compil")) return "编译失败，请检查代码";
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> item(Map<String, Object> raw, int index) {
        String stdout = firstString(raw, "stdout", "actualOutput", "actual_output");
        String stderr = firstString(raw, "stderr");
        String input = firstString(raw, "stdin", "input");
        String expected = firstString(raw, "expected_output", "expected");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("testCaseIndex", raw.get("testCaseIndex") != null ? raw.get("testCaseIndex") : raw.getOrDefault("index", index));
        item.put("passed", passed(raw));
        item.put("stdout", stdout);
        item.put("stderr", stderr);
        item.put("input", input);
        item.put("expected", expected);
        item.put("actualOutput", stdout);
        return item;
    }

    private boolean passed(Map<String, Object> raw) {
        Object passed = raw.get("passed");
        if (passed instanceof Boolean value) return value;
        Object status = raw.get("status");
        if (status instanceof Map<?, ?> map) {
            Object id = map.get("id");
            Object description = map.get("description");
            if (id instanceof Number number) return number.intValue() == 3;
            if (description != null) return "accepted".equalsIgnoreCase(String.valueOf(description));
        }
        if (status != null) {
            String text = String.valueOf(status).replace(" ", "").toLowerCase();
            return text.equals("ac") || text.equals("accepted");
        }
        return false;
    }

    private String firstString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof String text && !text.isBlank()) return text;
        }
        return "";
    }
}
