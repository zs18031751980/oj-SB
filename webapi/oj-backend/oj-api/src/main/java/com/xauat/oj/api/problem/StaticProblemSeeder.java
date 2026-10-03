package com.xauat.oj.api.problem;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 将静态题库幂等补齐到数据库（problems/testcases），保证：
 * 1. 普通提交的 problem_id 外键可用；
 * 2. 判题 Worker 能从 testcases 表读取静态题目的测试数据。
 * 与旧后端 {@code seed_problem_catalog} 一致，只补不覆盖已有题目。
 */
@Component
public class StaticProblemSeeder implements ApplicationRunner {
    private final StaticProblemCatalog catalog;
    private final JdbcTemplate jdbc;

    public StaticProblemSeeder(StaticProblemCatalog catalog, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ApplicationArguments args) {
        for (Map.Entry<Integer, Map<String, Object>> entry : catalog.all().entrySet()) {
            Integer id = entry.getKey();
            Map<String, Object> problem = entry.getValue();
            jdbc.update("insert into problems (id, title, description, input_desc, output_desc, difficulty, time_limit, memory_limit, is_public) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, true) on conflict (id) do nothing",
                    id, problem.get("title"), problem.getOrDefault("description", ""),
                    problem.getOrDefault("inputFormat", ""), problem.getOrDefault("outputFormat", ""),
                    problem.getOrDefault("difficulty", "简单"), problem.getOrDefault("timeLimit", 1000),
                    problem.getOrDefault("memoryLimit", 256));
            Integer existing = jdbc.queryForObject("select count(*) from testcases where problem_id = ?", Integer.class, id);
            if (existing != null && existing > 0) continue;
            Object testCases = problem.get("testCases");
            if (!(testCases instanceof List<?> list)) continue;
            int order = 0;
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> testCase)) continue;
                jdbc.update("insert into testcases (problem_id, input_data, output_data, is_sample, sort_order) values (?, ?, ?, false, ?)",
                        id, String.valueOf(testCase.get("input")), String.valueOf(testCase.get("output")), order++);
            }
        }
        resetSequence("problems");
        resetSequence("testcases");
    }

    private void resetSequence(String table) {
        try {
            jdbc.queryForObject("select setval(pg_get_serial_sequence('" + table + "', 'id'), greatest((select coalesce(max(id), 1) from " + table + "), 1))",
                    Long.class);
        } catch (RuntimeException ignored) {
            // 非 PostgreSQL 环境忽略；生产使用 PostgreSQL。
        }
    }
}
