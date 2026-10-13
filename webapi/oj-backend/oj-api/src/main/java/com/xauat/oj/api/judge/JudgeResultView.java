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

    /**
     * 解析数据库中保存的判题明细 JSON。
     *
     * @param payload JSON 字符串（可为空）
     * @return 规范化后的逐用例结果列表；解析失败返回空列表
     */
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

    /**
     * 找出第一个未通过用例的序号。
     *
     * @param results 逐用例结果
     * @return 首个失败用例索引；全部通过返回 {@code null}
     */
    public Integer failIndex(List<Map<String, Object>> results) {
        for (Map<String, Object> item : results) {
            if (!Boolean.TRUE.equals(item.get("passed"))) return (Integer) item.get("testCaseIndex");
        }
        return null;
    }

    /**
     * 根据状态文本识别编译错误。
     *
     * @param status 判题状态
     * @return 编译失败提示文案，否则 {@code null}
     */
    public String compileError(String status) {
        if (status == null) return null;
        // 兼容 "CE" 与各种含 "compil" 的状态描述。
        String normalized = status.replace(" ", "").toLowerCase();
        if (normalized.equals("ce") || normalized.contains("compil")) return "编译失败，请检查代码";
        return null;
    }

    /** 将单条原始结果适配为前端字段（兼容多种键名与状态结构）。 */
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

    /** 判断单条结果是否通过：兼容布尔 passed、Judge0 status.id==3 与文本状态。 */
    private boolean passed(Map<String, Object> raw) {
        Object passed = raw.get("passed");
        if (passed instanceof Boolean value) return value;
        Object status = raw.get("status");
        if (status instanceof Map<?, ?> map) {
            Object id = map.get("id");
            Object description = map.get("description");
            // Judge0 约定 status.id == 3 表示 Accepted。
            if (id instanceof Number number) return number.intValue() == 3;
            if (description != null) return "accepted".equalsIgnoreCase(String.valueOf(description));
        }
        if (status != null) {
            String text = String.valueOf(status).replace(" ", "").toLowerCase();
            return text.equals("ac") || text.equals("accepted");
        }
        return false;
    }

    /** 返回首个非空字符串字段，用于兼容多种键名。 */
    private String firstString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value instanceof String text && !text.isBlank()) return text;
        }
        return "";
    }
}
