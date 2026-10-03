package com.xauat.oj.api.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.domain.ContestTestcase;
import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.JudgementRepository;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.core.user.domain.User;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 比赛题目管理（旧后端命名空间 /admin/contests）：比赛本身由 /contests 管理。
 * 只有 DRAFT/READY 状态允许改题与测试数据，发布后冻结。
 */
@RestController
@RequestMapping("/admin/contests")
public class AdminContestController {
    private final CurrentUser currentUser;
    private final ContestRepository contests;
    private final ContestProblemRepository problems;
    private final ContestTestcaseRepository testcases;
    private final ContestSubmissionRepository submissions;
    private final JudgementRepository judgements;
    private final ContestJudgeOutboxRepository outboxes;
    private final ReferenceValidationJobRepository validationJobs;
    private final ObjectMapper mapper;

    public AdminContestController(CurrentUser currentUser, ContestRepository contests, ContestProblemRepository problems,
                                  ContestTestcaseRepository testcases, ContestSubmissionRepository submissions,
                                  JudgementRepository judgements, ContestJudgeOutboxRepository outboxes,
                                  ReferenceValidationJobRepository validationJobs, ObjectMapper mapper) {
        this.currentUser = currentUser; this.contests = contests; this.problems = problems; this.testcases = testcases;
        this.submissions = submissions; this.judgements = judgements; this.outboxes = outboxes;
        this.validationJobs = validationJobs; this.mapper = mapper;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false) String auth, @RequestParam(required = false) Integer contest_id) {
        manager(auth);
        if (contest_id == null) return ResponseEntity.badRequest().body(Map.of("error", "缺少 contest_id"));
        return ResponseEntity.ok(problems.findByContest_IdOrderBySortOrderAscIdAsc(contest_id).stream().map(this::view).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id) {
        manager(auth);
        ContestProblem problem = problems.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        Map<String, Object> body = view(problem);
        body.put("testcases", testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(id).stream().map(this::testcaseView).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping({"", "/"})
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestParam(required = false) Integer contest_id,
                                    @RequestBody Map<String, Object> body) {
        manager(auth);
        if (contest_id == null) return ResponseEntity.badRequest().body(Map.of("error", "缺少 contest_id"));
        Contest contest = contests.findById(contest_id).orElse(null);
        if (contest == null) return ResponseEntity.notFound().build();
        var editable = requireEditable(contest);
        if (editable != null) return editable;
        for (String field : List.of("problem_index", "title", "description", "correct_answer")) {
            if (str(body.get(field)).isBlank()) return ResponseEntity.badRequest().body(Map.of("error", field + " 不能为空"));
        }
        int[] limits;
        try { limits = validateLimits(body.get("time_limit"), body.get("memory_limit")); }
        catch (IllegalArgumentException exception) { return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage())); }
        List<Map<String, Object>> normalized;
        try { normalized = normalizeTestcases(body.get("testcases"), body.get("samples")); }
        catch (IllegalArgumentException exception) { return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage())); }

        ContestProblem problem = ContestProblem.create(contest, str(body.get("problem_index")).trim(), str(body.get("title")).trim(),
                str(body.get("description")), str(body.get("input_desc")), str(body.get("output_desc")),
                str(body.get("correct_answer")), limits[0], limits[1], strOrDefault(body.get("difficulty"), "中等"),
                strOrDefault(body.get("language"), "cpp"), samplesJson(body.get("samples")), intOrDefault(body.get("sort_order"), 0));
        problems.save(problem);
        replaceTestcases(problem, normalized);
        validationJobs.save(ReferenceValidationJob.create(UUID.randomUUID().toString(), problem));
        Map<String, Object> result = view(problem);
        result.put("testcase_generation", "pending");
        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        manager(auth);
        ContestProblem problem = problems.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        Contest contest = contests.findById(problem.getContestId()).orElse(null);
        var editable = requireEditable(contest);
        if (editable != null) return editable;
        Integer timeLimit = body.containsKey("time_limit") ? intOrDefault(body.get("time_limit"), problem.getTimeLimit()) : null;
        Integer memoryLimit = body.containsKey("memory_limit") ? intOrDefault(body.get("memory_limit"), problem.getMemoryLimit()) : null;
        try { validateLimits(timeLimit == null ? problem.getTimeLimit() : timeLimit, memoryLimit == null ? problem.getMemoryLimit() : memoryLimit); }
        catch (IllegalArgumentException exception) { return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage())); }
        problem.updateAsset(str(body.get("problem_index")), str(body.get("title")), body.containsKey("description") ? str(body.get("description")) : null,
                body.containsKey("input_desc") ? str(body.get("input_desc")) : null, body.containsKey("output_desc") ? str(body.get("output_desc")) : null,
                body.containsKey("correct_answer") ? str(body.get("correct_answer")) : null, timeLimit, memoryLimit,
                body.containsKey("difficulty") ? str(body.get("difficulty")) : null, body.containsKey("language") ? str(body.get("language")) : null,
                body.containsKey("samples") ? samplesJson(body.get("samples")) : null,
                body.containsKey("sort_order") ? intOrDefault(body.get("sort_order"), problem.getSortOrder()) : null);
        List<Map<String, Object>> replacement = null;
        if (body.containsKey("testcases") || body.containsKey("samples")) {
            try { replacement = normalizeTestcases(body.get("testcases"), body.get("samples")); }
            catch (IllegalArgumentException exception) { return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage())); }
        }
        problem.bumpValidation();
        problems.save(problem);
        if (replacement != null) replaceTestcases(problem, replacement);
        validationJobs.save(ReferenceValidationJob.create(UUID.randomUUID().toString(), problem));
        return ResponseEntity.ok(view(problem));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id) {
        manager(auth);
        ContestProblem problem = problems.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        Contest contest = contests.findById(problem.getContestId()).orElse(null);
        var editable = requireEditable(contest);
        if (editable != null) return editable;
        try {
            judgements.deleteByProblemId(id);
            outboxes.deleteByProblemId(id);
            submissions.deleteByProblemId(id);
            testcases.deleteByProblemId(id);
            problems.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(Map.of("error", "服务暂时不可用"));
        }
    }

    /** 自动随机生成已停用：必须由出题人提供经过验证的测试数据。 */
    @PostMapping("/{id}/regenerate-testcases")
    public ResponseEntity<?> regenerate(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id) {
        manager(auth);
        if (!problems.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.status(409).body(Map.of("error", "自动随机生成已停用，请在编辑题目时提交经过验证的测试数据"));
    }

    @GetMapping("/{id}/testcase-generation")
    public ResponseEntity<?> generation(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id) {
        manager(auth);
        ContestProblem problem = problems.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        int count = testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(id).size();
        String status = switch (problem.getValidationStatus() == null ? "PENDING" : problem.getValidationStatus()) {
            case "VALID" -> "done";
            case "INVALID" -> "error";
            default -> "pending";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("total", count);
        body.put("generated", "done".equals(status) ? count : 0);
        body.put("error", problem.getValidationError());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<?> requireEditable(Contest contest) {
        if (contest == null) return ResponseEntity.notFound().build();
        String state = contest.getLifecycleState();
        if (!List.of("DRAFT", "READY").contains(state)) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛已发布，题目与测试数据已冻结"));
        }
        return null;
    }

    private int[] validateLimits(Object timeLimit, Object memoryLimit) {
        int timeMs = intOrDefault(timeLimit, 1000);
        int memoryMb = intOrDefault(memoryLimit, 256);
        if (timeMs < 50 || timeMs > 60000) throw new IllegalArgumentException("时间限制必须在 50ms 到 60000ms 之间");
        if (memoryMb < 16 || memoryMb > 2048) throw new IllegalArgumentException("内存限制必须在 16MB 到 2048MB 之间");
        return new int[]{timeMs, memoryMb};
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeTestcases(Object rawTestcases, Object rawSamples) {
        Object source = rawTestcases != null ? rawTestcases : rawSamples;
        if (source == null) throw new IllegalArgumentException("至少需要提供一组测试数据");
        if (source instanceof String text) {
            if (text.isBlank()) throw new IllegalArgumentException("至少需要提供一组测试数据");
            try { source = mapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {}); }
            catch (Exception exception) { throw new IllegalArgumentException("测试数据必须是合法 JSON"); }
        }
        if (!(source instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("至少需要提供一组测试数据");
        if (list.size() > 200) throw new IllegalArgumentException("测试数据最多 200 组");
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> item) || !item.containsKey("input") || !item.containsKey("output")) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 组测试数据必须包含 input 与 output");
            }
            Object input = item.get("input"); Object output = item.get("output");
            if (!(input instanceof String inputText) || !(output instanceof String outputText)) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 组测试数据的 input 与 output 必须是字符串");
            }
            if (inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536
                    || outputText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 组测试数据过大（上限 64KB）");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("input", inputText);
            entry.put("output", outputText);
            entry.put("is_sample", item.get("is_sample") == Boolean.TRUE || (rawTestcases == null));
            normalized.add(entry);
        }
        return normalized;
    }

    private void replaceTestcases(ContestProblem problem, List<Map<String, Object>> normalized) {
        testcases.deleteByProblemId(problem.getId());
        int order = 0;
        for (Map<String, Object> item : normalized) {
            testcases.save(ContestTestcase.create(problem, String.valueOf(item.get("input")), String.valueOf(item.get("output")),
                    Boolean.TRUE.equals(item.get("is_sample")), order++));
        }
    }

    private Map<String, Object> view(ContestProblem problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", problem.getId());
        body.put("contest_id", problem.getContestId());
        body.put("problem_index", problem.getProblemIndex());
        body.put("title", problem.getTitle());
        body.put("description", problem.getDescription());
        body.put("input_desc", problem.getInputDesc());
        body.put("output_desc", problem.getOutputDesc());
        body.put("correct_answer", problem.getCorrectAnswer());
        body.put("time_limit", problem.getTimeLimit());
        body.put("memory_limit", problem.getMemoryLimit());
        body.put("difficulty", problem.getDifficulty());
        body.put("language", problem.getLanguage());
        body.put("samples", parseSamples(problem.getSamples()));
        body.put("sort_order", problem.getSortOrder());
        body.put("validation_version", problem.getValidationVersion());
        body.put("validation_status", problem.getValidationStatus());
        body.put("validation_error", problem.getValidationError());
        body.put("testcase_count", testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId()).size());
        return body;
    }

    private Map<String, Object> testcaseView(ContestTestcase item) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", item.getId());
        body.put("input_data", item.getInputData());
        body.put("expected_output", item.getExpectedOutput());
        body.put("is_sample", item.isSample());
        body.put("sort_order", item.getSortOrder());
        return body;
    }

    private List<Map<String, Object>> parseSamples(String raw) {
        try {
            if (raw == null || raw.isBlank()) return List.of();
            return mapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private User manager(String auth) {
        User user = currentUser.require(auth);
        if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足");
        }
        return user;
    }

    private String samplesJson(Object value) {
        if (value == null) return "[]";
        if (value instanceof String text) return text.isBlank() ? "[]" : text;
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "[]"; }
    }

    private static String str(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String strOrDefault(Object value, String fallback) { String text = str(value); return text.isBlank() ? fallback : text; }
    private static int intOrDefault(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException exception) { return fallback; } }
}
