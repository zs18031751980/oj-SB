package com.xauat.oj.api.contest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestPackage;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.repository.ContestPackageRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.infrastructure.judge.Checker;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 内容寻址题包：canonical JSON + SHA-256 摘要，先 stage(PENDING) 再由 Worker 验证，验证通过后才能 activate。
 */
@Service
public class PackageService {
    private static final Set<String> LANGUAGES = Set.of("cpp", "python", "java", "go", "javascript");

    private final ContestPackageRepository packages;
    private final ContestProblemRepository problems;
    private final ContestTestcaseRepository testcases;
    private final ContestRepository contests;
    private final ObjectMapper mapper;

    public PackageService(ContestPackageRepository packages, ContestProblemRepository problems,
                          ContestTestcaseRepository testcases, ContestRepository contests, ObjectMapper mapper) {
        this.packages = packages; this.problems = problems; this.testcases = testcases; this.contests = contests; this.mapper = mapper;
    }

    /** 发布时按当前题目与测试数据生成内容寻址题包（VALID），写入 package_digest。 */
    public ContestPackage publish(ContestProblem problem, Integer actorId) {
        var cases = testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId());
        if (cases.isEmpty()) throw new IllegalArgumentException("题包没有测试数据");
        List<Map<String, Object>> normalizedCases = new ArrayList<>();
        for (var testcase : cases) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("input_data", testcase.getInputData());
            entry.put("expected_output", testcase.getExpectedOutput());
            entry.put("is_sample", testcase.isSample());
            normalizedCases.add(entry);
        }
        Map<String, Object> checkerConfig;
        try { checkerConfig = mapper.readValue(problem.getCheckerConfig(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { checkerConfig = Map.of("checker", "text"); }
        Contest contest = contests.findById(problem.getContestId()).orElse(null);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("problem_id", problem.getId());
        payloadMap.put("reference", problem.getCorrectAnswer());
        payloadMap.put("language", problem.getLanguage());
        payloadMap.put("cases", normalizedCases);
        payloadMap.put("checker_config", checkerConfig);
        payloadMap.put("time_limit", problem.getTimeLimit());
        payloadMap.put("memory_limit", problem.getMemoryLimit());
        payloadMap.put("validator", null);
        payloadMap.put("known_wrong", List.of());
        payloadMap.put("language_limits", Map.of());
        payloadMap.put("runtime_image", System.getenv().getOrDefault("JUDGE_SANDBOX_IMAGE", "letcoding-sandbox:local"));
        payloadMap.put("rules_version", contest == null ? "acm-2026-v1" : contest.getRulesVersion());
        String payload = canonical(payloadMap);
        String digest = sha256(payload);
        return packages.findById(digest).orElseGet(() -> packages.save(ContestPackage.create(digest, problem, actorId, payload)));
    }

    @SuppressWarnings("unchecked")
    public ContestPackage stage(ContestProblem problem, Map<String, Object> data, Integer actorId) {
        if (data == null) throw new IllegalArgumentException("无效题包");
        Object casesRaw = data.get("cases");
        if (!(casesRaw instanceof List<?> cases) || cases.isEmpty() || cases.size() > 1000) {
            throw new IllegalArgumentException("题包需要 1 至 1000 个测试点");
        }
        List<Map<String, Object>> normalizedCases = new ArrayList<>();
        boolean hasHidden = false;
        for (int i = 0; i < cases.size(); i++) {
            if (!(cases.get(i) instanceof Map<?, ?> testcase) || !(testcase.get("input_data") instanceof String inputData)
                    || !(testcase.get("expected_output") instanceof String expectedOutput)) {
                throw new IllegalArgumentException("测试数据必须为字符串");
            }
            boolean sample = testcase.get("is_sample") == Boolean.TRUE;
            hasHidden |= !sample;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("input_data", inputData); entry.put("expected_output", expectedOutput); entry.put("is_sample", sample);
            normalizedCases.add(entry);
        }
        if (!hasHidden) throw new IllegalArgumentException("缺少隐藏测试");

        Map<String, Object> checkerConfig = data.get("checker_config") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of("checker", "text");
        validateChecker(checkerConfig);
        String reference = data.get("reference") instanceof String text ? text : problem.getCorrectAnswer();
        if (reference == null || reference.isBlank() || reference.getBytes(StandardCharsets.UTF_8).length > 131072) {
            throw new IllegalArgumentException("参考答案为空或过长");
        }
        String language = data.get("language") instanceof String value ? value : problem.getLanguage();
        if (!LANGUAGES.contains(language)) throw new IllegalArgumentException("无效参考语言");
        int timeLimit = intOrDefault(data.get("time_limit"), problem.getTimeLimit());
        int memoryLimit = intOrDefault(data.get("memory_limit"), problem.getMemoryLimit());
        if (timeLimit < 1 || timeLimit > 30000) throw new IllegalArgumentException("判题资源限制无效");
        if (memoryLimit < 16 || memoryLimit > 2048) throw new IllegalArgumentException("判题资源限制无效");

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("problem_id", problem.getId());
        payloadMap.put("reference", reference);
        payloadMap.put("language", language);
        payloadMap.put("cases", normalizedCases);
        payloadMap.put("checker_config", checkerConfig);
        payloadMap.put("time_limit", timeLimit);
        payloadMap.put("memory_limit", memoryLimit);
        payloadMap.put("validator", data.get("validator"));
        payloadMap.put("known_wrong", data.get("known_wrong") == null ? List.of() : data.get("known_wrong"));
        payloadMap.put("language_limits", data.get("language_limits") == null ? Map.of() : data.get("language_limits"));
        payloadMap.put("runtime_image", System.getenv().getOrDefault("JUDGE_SANDBOX_IMAGE", "letcoding-sandbox:local"));
        payloadMap.put("rules_version", problem.getDescription() == null ? "acm-2026-v1" : "acm-2026-v1");
        String payload = canonical(payloadMap);
        if (payload.getBytes(StandardCharsets.UTF_8).length > 8 * 1024 * 1024) throw new IllegalArgumentException("题包超过 8 MiB");
        String digest = sha256(payload);
        return packages.findById(digest).orElseGet(() -> packages.save(ContestPackage.staged(digest, problem, actorId, payload)));
    }

    public ContestPackage activate(String digest, Integer actorId) {
        ContestPackage pkg = packages.findById(digest).orElseThrow(() -> new IllegalArgumentException("题包不存在"));
        if (!"VALID".equals(pkg.getValidationState())) throw new IllegalStateException("题包尚未验证通过");
        try {
            Map<String, Object> data = mapper.readValue(pkg.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
            ContestProblem problem = problems.findById(pkg.getProblemId()).orElseThrow(() -> new IllegalArgumentException("题目不存在"));
            Object checkerConfig = data.get("checker_config");
            problem.activatePackage(digest, intOrDefault(data.get("time_limit"), problem.getTimeLimit()),
                    intOrDefault(data.get("memory_limit"), problem.getMemoryLimit()),
                    checkerConfig == null ? "{\"checker\":\"text\"}" : canonical(checkerConfig),
                    String.valueOf(data.get("reference")), String.valueOf(data.get("language")));
            problems.save(problem);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("题包负载损坏");
        }
        return pkg;
    }

    private void validateChecker(Map<String, Object> config) {
        String checker = config.get("checker") instanceof String value ? value : "text";
        if (!Checker.supportedName(checker)) throw new IllegalArgumentException("Unsupported checker");
        if ("custom".equals(checker)) {
            Object language = config.get("language");
            Object code = config.get("code");
            if (!(language instanceof String lang) || !(lang.equals("python") || lang.equals("cpp"))
                    || !(code instanceof String source) || source.isBlank()
                    || source.getBytes(StandardCharsets.UTF_8).length > 65536) {
                throw new IllegalArgumentException("Invalid checker program");
            }
        }
        for (String key : List.of("absolute_tolerance", "relative_tolerance")) {
            if (config.get(key) == null) continue;
            double value = doubleOrDefault(config.get(key), 1e-6);
            if (!Double.isFinite(value) || value < 0 || value > 0.01) throw new IllegalArgumentException("Invalid tolerance");
        }
    }

    /** 递归排序对象键并紧凑序列化，保证相同内容得到相同摘要。 */
    @SuppressWarnings("unchecked")
    public String canonical(Object value) {
        try { return mapper.writeValueAsString(sort(value)); }
        catch (Exception exception) { throw new IllegalStateException("无法序列化题包"); }
    }

    @SuppressWarnings("unchecked")
    private Object sort(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), sort(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(sort(item));
            return result;
        }
        if (value instanceof Double number && !Double.isFinite(number)) throw new IllegalStateException("allow_nan violated");
        return value;
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static int intOrDefault(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException exception) { return fallback; } }
    private static double doubleOrDefault(Object value, double fallback) { try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException exception) { return fallback; } }
}
