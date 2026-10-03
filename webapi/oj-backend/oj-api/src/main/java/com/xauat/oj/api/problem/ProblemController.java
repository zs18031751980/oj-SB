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

@RestController
@RequestMapping("/problems")
public class ProblemController {
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

    @GetMapping({"", "/"})
    public Map<String, Object> list() {
        List<Map<String, Object>> data = catalog.list();
        return Map.of("data", data, "total", data.size());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Integer id) {
        return catalog.detail(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/library/submit")
    @Transactional
    public ResponseEntity<?> librarySubmit(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @Valid @RequestBody LibraryRequest request) {
        var user = currentUser.require(auth);
        if (key != null && key.length() > 128) return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        ContestProblem problem = catalog.findLibraryProblem(request.contestProblemId()).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        Contest contest = contests.findById(problem.getContestId()).orElse(null);
        if (contest == null) return ResponseEntity.notFound().build();
        String normalizedKey = key == null ? null : "library:" + sha256(key);
        if (normalizedKey != null) {
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
        if (submissions.countByUser_IdAndContestEligibleFalseAndRejudgeOfIsNullAndStatusNotIn(user.getId(), TERMINAL_STATES) >= 3) {
            return ResponseEntity.status(429).body(Map.of("error", "最多同时处理 3 个比赛题目提交"));
        }
        ContestSubmission submission = ContestSubmission.create(contest, user, problem, request.code(), request.language(), UUID.randomUUID().toString(), normalizedKey);
        submission.asPractice();
        submissions.save(submission);
        outboxes.save(ContestJudgeOutbox.of(submission));
        return ResponseEntity.status(202).body(Map.of("submission_id", submission.getId(), "status", submission.getStatus(), "queue_pending_retry", false));
    }

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

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public record LibraryRequest(@com.fasterxml.jackson.annotation.JsonProperty("contest_problem_id") Integer contestProblemId,
                                 @NotBlank String code, @NotBlank String language) {}
}
