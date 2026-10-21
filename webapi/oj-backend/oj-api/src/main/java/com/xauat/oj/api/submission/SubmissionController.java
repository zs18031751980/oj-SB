package com.xauat.oj.api.submission;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.api.judge.JudgeResultView;
import com.xauat.oj.api.problem.StaticProblemCatalog;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import com.xauat.oj.core.submission.domain.Submission;
import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 普通题目提交接口（{@code /submissions}）：历史列表、创建提交与轮询结果。
 *
 * <p>创建提交时先落库并写 outbox，由 {@link SubmissionOutboxDispatcher} 事务提交后投递判题队列；
 * 结果查询使用短缓存降低轮询压力。</p>
 */
@RestController
@RequestMapping("/submissions")
public class SubmissionController {
    /** 未完成状态：用于限制用户并发提交数。 */
    private static final List<String> PENDING_STATES = List.of("Pending", "Judging");
    private final CurrentUser currentUser;
    private final ProblemRepository problems;
    private final SubmissionRepository submissions;
    private final SubmissionOutboxRepository outboxes;
    private final StaticProblemCatalog staticCatalog;
    private final JudgeResultView judgeResultView;
    private final SubmissionCache cache;

    public SubmissionController(CurrentUser currentUser, ProblemRepository problems, SubmissionRepository submissions,
            SubmissionOutboxRepository outboxes, StaticProblemCatalog staticCatalog, JudgeResultView judgeResultView,
            SubmissionCache cache) {
        this.currentUser = currentUser;
        this.problems = problems;
        this.submissions = submissions;
        this.outboxes = outboxes;
        this.staticCatalog = staticCatalog;
        this.judgeResultView = judgeResultView;
        this.cache = cache;
    }

    /**
     * 分页列出当前用户的提交历史。
     *
     * @param authorization 认证头
     * @param page          页码（从 1 开始）
     * @param per_page      每页条数（上限 50）
     * @return 分页结果
     */
    @GetMapping({"", "/"})
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @RequestParam(defaultValue = "1")
            Integer page,
            @RequestParam(defaultValue = "20")
            Integer per_page) {
        var user = currentUser.require(authorization);
        // 数据库分页（避免加载用户全部提交），per_page 上限 50，按 id 倒序。
        int safePage = Math.max(1, page);
        int size = Math.max(1, Math.min(per_page, 50));
        var pageResult = submissions.findByUser_Id(user.getId(),
                org.springframework.data.domain.PageRequest.of(safePage - 1, size,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")));
        long total = pageResult.getTotalElements();
        List<Map<String, Object>> data = pageResult.getContent().stream().map(this::historyItem).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("page", safePage);
        body.put("per_page", size);
        body.put("data", data);
        return body;
    }

    /**
     * 创建普通题目提交。
     *
     * <p>需登录；仅允许静态题库中的题目。支持 {@code Idempotency-Key} 幂等回放；
     * 同一用户未完成提交最多 3 个。提交落库后写 outbox 异步投递判题。</p>
     *
     * @param authorization  认证头
     * @param idempotencyKey 幂等键（可空，最长 128）
     * @param request        题目 id、代码与语言
     * @return 201 与提交 id/状态；限流返回 429，参数错误返回 4xx
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody
            SubmissionRequest request) {
        var user = currentUser.require(authorization);
        if (idempotencyKey != null && idempotencyKey.length() > 128)
            return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        if (!staticCatalog.contains(request.problemId()))
            return ResponseEntity.notFound().build();
        if (idempotencyKey != null) {
            // 命中幂等键直接回放原提交。
            var replay = submissions.findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey);
            if (replay.isPresent())
                return ResponseEntity.status(201)
                        .body(Map.of("id", replay.get().getId(), "status", replay.get().getStatus(), "problem_id",
                                replay.get().getProblemId(), "idempotent_replay", true));
        }
        // 并发限制：未完成提交达 3 个即拒绝。
        if (submissions.countByUser_IdAndStatusIn(user.getId(), PENDING_STATES) >= 3) {
            return ResponseEntity.status(429).body(Map.of("error", "最多同时处理 3 个提交"));
        }
        var problem = problems.findById(request.problemId()).orElse(null);
        if (problem == null)
            return ResponseEntity.notFound().build();
        String jobId = UUID.randomUUID().toString();
        Submission submission = submissions
                .save(Submission.create(user, problem, request.code(), request.language(), jobId, idempotencyKey));
        // 写 outbox，保证 DB 提交后再投递判题队列。
        outboxes.save(SubmissionOutbox.of(submission));
        return ResponseEntity.status(201).body(Map.of("id", submission.getId(), "status", submission.getStatus(),
                "problem_id", submission.getProblemId()));
    }

    /**
     * 查询提交详情（轮询端点）。
     *
     * <p>权限：提交者本人或 manager。命中缓存时直接返回；终态结果才写缓存。</p>
     *
     * @param authorization 认证头
     * @param id            提交 id
     * @return 判题结果；不存在或无权返回 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        var user = currentUser.require(authorization);
        String key = "cache:submission:" + id;
        var cached = cache.get(key);
        if (cached.isPresent()) {
            // 校验缓存归属，避免越权读取他人提交。
            Object owner = cached.get().get("user_id");
            if (owner == null || owner.toString().equals(user.getId().toString())
                    || "manager".equalsIgnoreCase(user.getRole())) {
                return ResponseEntity.ok(cached.get());
            }
        }
        return submissions.findById(id)
                .filter(item -> item.getUserId().equals(user.getId()) || "manager".equalsIgnoreCase(user.getRole()))
                .<ResponseEntity<?>>map(item -> {
                    Map<String, Object> body = result(item);
                    // 仅终态写入缓存，进行中的结果不缓存。
                    if (isTerminal(item.getStatus()))
                        cache.put(key, body);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 组装提交详情响应：状态、耗时/内存、逐用例结果与失败/编译信息。 */
    private Map<String, Object> result(Submission item) {
        List<Map<String, Object>> cases = judgeResultView.parse(item.getTestcaseResults());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", item.getId());
        body.put("user_id", item.getUserId());
        body.put("problem_id", item.getProblemId());
        body.put("status", item.getStatus());
        body.put("language", item.getLanguage());
        body.put("time_used", item.getTimeUsed());
        body.put("memory_used", item.getMemoryUsed());
        body.put("testcase_results", cases);
        body.put("fail_testcase_index",
                item.getFailTestcaseIndex() != null ? item.getFailTestcaseIndex() : judgeResultView.failIndex(cases));
        body.put("compile_error", judgeResultView.compileError(item.getStatus()));
        body.put("created_at", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        return body;
    }

    /** 判断状态是否为终态（非进行中的状态即为终态）。 */
    private static boolean isTerminal(String status) {
        if (status == null)
            return false;
        return !List.of("Pending", "Queued", "Judging", "Claimed", "Compiling", "Compiled", "Running", "Checking")
                .contains(status);
    }

    /** 组装提交历史列表项，附带题目标题与难度。 */
    private Map<String, Object> historyItem(Submission item) {
        var problem = item.getProblem();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", item.getId());
        body.put("problem_id", item.getProblemId());
        body.put("problem_title", problem == null ? "" : problem.getTitle());
        body.put("difficulty", problem == null ? null : problem.getDifficulty());
        body.put("language", item.getLanguage());
        body.put("status", item.getStatus());
        body.put("time_used", item.getTimeUsed());
        body.put("created_at", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        return body;
    }
    /** 普通提交请求体，兼容 {@code problem_id} / {@code problemId} 字段名。 */
    public record SubmissionRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("problem_id")
            @com.fasterxml.jackson.annotation.JsonAlias("problemId")
            @NotNull
            Integer problemId,
            @NotBlank @jakarta.validation.constraints.Size(max = 131072)
            String code,
            @NotBlank @jakarta.validation.constraints.Size(max = 50)
            String language) {
    }
}
