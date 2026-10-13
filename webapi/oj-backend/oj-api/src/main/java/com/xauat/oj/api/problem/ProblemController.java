package com.xauat.oj.api.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.api.judge.JudgeResultView;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 题库接口（{@code /problems}）：公开题面，以及对已结束比赛题目的库内提交与结果查询。
 *
 * <p>普通提交走 {@code /submissions}；这里只处理“题库（练习）提交”，
 * 其提交不参与比赛计分，并用 outbox 保证事务提交后再投递判题队列。</p>
 */
@RestController
@RequestMapping("/problems")
public class ProblemController {
    /** 判题终态：处于这些状态的提交不再占用并发额度。 */
    private static final List<String> TERMINAL_STATES = List.of(
            "AC", "WA", "CE", "TLE", "MLE", "OLE", "RE", "SIGSEGV", "SIGSYS", "SystemError", "Cancelled", "Partial");

    private final ProblemCatalogService catalog;
    private final CurrentUser currentUser;
    private final ContestRepository contests;
    private final ContestSubmissionRepository submissions;
    private final ContestJudgeOutboxRepository outboxes;
    private final JudgeResultView judgeResultView;

    public ProblemController(ProblemCatalogService catalog, CurrentUser currentUser,
                             ContestRepository contests, ContestSubmissionRepository submissions,
                             ContestJudgeOutboxRepository outboxes, JudgeResultView judgeResultView) {
        this.catalog = catalog; this.currentUser = currentUser;
        this.contests = contests; this.submissions = submissions; this.outboxes = outboxes;
        this.judgeResultView = judgeResultView;
    }

    /**
     * 列出题库全部题目。
     *
     * @return 题目摘要列表与总数
     */
    @GetMapping({"", "/"})
    public Map<String, Object> list() {
        List<Map<String, Object>> data = catalog.list();
        return Map.of("data", data, "total", data.size());
    }

    /**
     * 查看题目详情（公开）。
     *
     * @param id 题库 id
     * @return 题目详情；不存在返回 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Integer id) {
        return catalog.detail(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 提交库内（练习）题目。
     *
     * <p>需登录。支持 {@code Idempotency-Key} 幂等：键会加 {@code library:} 前缀并做
     * SHA-256 归一化，重复提交返回原结果；同一用户最多 3 个未完成提交。
     * 提交以练习身份落库并写入 outbox，由派发器投递判题队列。</p>
     *
     * @param auth    认证头
     * @param key     幂等键（可空，最长 128）
     * @param request 比赛题目 id、代码与语言
     * @return 202 与提交 id/状态；参数或幂等冲突返回 4xx
     */
    @PostMapping("/library/submit")
    @Transactional
    public ResponseEntity<?> librarySubmit(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @Valid @RequestBody LibraryRequest request) {
        var user = currentUser.require(auth);
        if (key != null && key.length() > 128) return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        // 只允许提交已结束比赛释放到题库的题目。
        ContestProblem problem = catalog.findLibraryProblem(request.contestProblemId()).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        Contest contest = contests.findById(problem.getContestId()).orElse(null);
        if (contest == null) return ResponseEntity.notFound().build();
        String normalizedKey = key == null ? null : "library:" + sha256(key);
        if (normalizedKey != null) {
            // 命中幂等键：校验载荷一致则直接回放，否则视为键复用冲突。
            var replay = submissions.findByContest_IdAndUser_IdAndIdempotencyKey(contest.getId(), user.getId(), normalizedKey);
            if (replay.isPresent()) {
                ContestSubmission existing = replay.get();
                if (!existing.getCode().equals(request.code()) || !existing.getLanguage().equalsIgnoreCase(request.language())
                        || !problem.getId().equals(existing.getContestProblemId())) {
                    return ResponseEntity.status(409).body(Map.of("error", "幂等键已用于不同提交"));
                }
                return ResponseEntity.status(202).body(Map.of("submission_id", existing.getId(), "status", existing.getStatus(), "idempotent_replay", true));
            }
        }
        // 并发提交限制：未处于终态的库内提交（非重测）最多 3 个。
        if (submissions.countByUser_IdAndContestEligibleFalseAndRejudgeOfIsNullAndStatusNotIn(user.getId(), TERMINAL_STATES) >= 3) {
            return ResponseEntity.status(429).body(Map.of("error", "最多同时处理 3 个比赛题目提交"));
        }
        ContestSubmission submission = ContestSubmission.create(contest, user, problem, request.code(), request.language(), UUID.randomUUID().toString(), normalizedKey);
        // 标记为练习提交，不参与比赛榜单/计分。
        submission.asPractice();
        submissions.save(submission);
        // 写 outbox，保证 DB 提交后再异步投递判题任务。
        outboxes.save(ContestJudgeOutbox.of(submission));
        return ResponseEntity.status(202).body(Map.of("submission_id", submission.getId(), "status", submission.getStatus(), "queue_pending_retry", false));
    }

    /**
     * 查询库内提交结果。
     *
     * <p>权限：提交者本人或 manager。出于隐藏测试数据考虑，不回传输入/期望输出/实际输出。</p>
     *
     * @param auth 认证头
     * @param id   提交 id
     * @return 判题结果；不存在或无权返回 404
     */
    @GetMapping("/library/submission/{id}")
    public ResponseEntity<?> librarySubmission(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable Integer id) {
        var user = currentUser.require(auth);
        ContestSubmission submission = submissions.findById(id).filter(item -> !item.isContestEligible()
                && item.getRejudgeOfId() == null
                && (user.getId().equals(item.getUserId()) || "manager".equalsIgnoreCase(user.getRole()))).orElse(null);
        if (submission == null) return ResponseEntity.notFound().build();
        // 隐藏测试数据：不向参赛者回传输入、期望输出与实际输出。
        List<Map<String, Object>> cases = judgeResultView.parse(submission.getTestcaseResults()).stream().map(item -> {
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("testCaseIndex", item.get("testCaseIndex"));
            safe.put("passed", item.get("passed"));
            safe.put("stdout", "");
            safe.put("stderr", "");
            safe.put("expected", "");
            safe.put("input", "");
            return safe;
        }).toList();
        return ResponseEntity.ok(libraryResult(submission, cases));
    }

    /** 组装库内提交的对外结果字段。 */
    private Map<String, Object> libraryResult(ContestSubmission submission, List<Map<String, Object>> cases) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", submission.getId());
        body.put("status", submission.getStatus());
        body.put("time_used", submission.getCpuTime());
        body.put("memory_used", submission.getMemory());
        body.put("testcase_results", cases);
        body.put("fail_testcase_index", cases.isEmpty() ? null : judgeResultView.failIndex(cases));
        body.put("compile_error", judgeResultView.compileError(submission.getStatus()));
        return body;
    }

    /** 计算 SHA-256 十六进制摘要，用于幂等键归一化。 */
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    /** 库内提交请求体：比赛题目 id、代码与语言。 */
    public record LibraryRequest(@com.fasterxml.jackson.annotation.JsonProperty("contest_problem_id") Integer contestProblemId,
                                 @NotBlank String code, @NotBlank String language) {}
}
