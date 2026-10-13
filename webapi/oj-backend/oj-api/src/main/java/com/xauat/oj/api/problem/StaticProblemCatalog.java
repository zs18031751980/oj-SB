package com.xauat.oj.api.problem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 加载由旧后端页面数据导出的静态题库（含测试数据），保证题面与判题数据完全一致。
 * 测试数据仅在后端判题时使用，绝不会通过 API 下发。
 */
@Component
public class StaticProblemCatalog {
    public static final int LIBRARY_ID_BASE = 1_000_000;
    private final Map<Integer, Map<String, Object>> problems = new LinkedHashMap<>();

    /**
     * 从类路径 {@code /catalog/problems.json} 载入静态题库。
     *
     * @param mapper JSON 映射器
     * @throws IllegalStateException 资源缺失或解析失败时抛出
     */
    public StaticProblemCatalog(ObjectMapper mapper) {
        try (InputStream input = getClass().getResourceAsStream("/catalog/problems.json")) {
            if (input == null)
                throw new IllegalStateException("缺少 /catalog/problems.json");
            // problems 为 {id: {题目字段...}} 结构，按数值 id 建索引。
            JsonNode root = mapper.readTree(input);
            JsonNode entries = root.path("problems");
            entries.fields().forEachRemaining(entry -> problems.put(Integer.valueOf(entry.getKey()),
                    mapper.convertValue(entry.getValue(), new TypeReference<LinkedHashMap<String, Object>>() {
                    })));
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载静态题库", exception);
        }
    }

    /** 按 id 查找静态题目。 */
    public Optional<Map<String, Object>> find(Integer id) {
        return Optional.ofNullable(problems.get(id));
    }
    /** 判断 id 是否属于静态题库。 */
    public boolean contains(Integer id) {
        return problems.containsKey(id);
    }
    /** 返回全部静态题目（id 升序由调用方排序）。 */
    public Map<Integer, Map<String, Object>> all() {
        return problems;
    }
}
