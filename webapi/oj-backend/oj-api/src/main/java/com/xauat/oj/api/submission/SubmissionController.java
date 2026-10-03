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

@RestController
@RequestMapping("/submissions")
public class SubmissionController {
    private static final List<String> PENDING_STATES = List.of("Pending", "Judging");
    private final CurrentUser currentUser; private final ProblemRepository problems; private final SubmissionRepository submissions;
    private final SubmissionOutboxRepository outboxes; private final StaticProblemCatalog staticCatalog; private final JudgeResultView judgeResultView;
    private final SubmissionCache cache;

    public SubmissionController(CurrentUser currentUser, ProblemRepository problems, SubmissionRepository submissions,
                                SubmissionOutboxRepository outboxes, StaticProblemCatalog staticCatalog, JudgeResultView judgeResultView,
                                SubmissionCache cache) {
        this.currentUser = currentUser; this.problems = problems; this.submissions = submissions;
        this.outboxes = outboxes; this.staticCatalog = staticCatalog; this.judgeResultView = judgeResultView; this.cache = cache;
    }

    @GetMapping({"", "/"})
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer per_page) {
        var all = submissions.findByUser_IdOrderByIdDesc(currentUser.require(authorization).getId()); long total = all.size();
        int safePage = Math.max(1, page); int size = Math.max(1, Math.min(per_page, 50));
        int from = Math.min((int) total, (safePage - 1) * size); int to = Math.min((int) total, from + size);
        List<Map<String, Object>> data = all.subList(from, to).stream().map(this::historyItem).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total); body.put("page", safePage); body.put("per_page", size); body.put("data", data);
        return body;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @Valid @RequestBody SubmissionRequest request) {
        var user = currentUser.require(authorization);
        if (idempotencyKey != null && idempotencyKey.length() > 128) return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        if (!staticCatalog.contains(request.problemId())) return ResponseEntity.notFound().build();
        if (idempotencyKey != null) {
            var replay = submissions.findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey);
            if (replay.isPresent()) return ResponseEntity.status(201).body(Map.of("id", replay.get().getId(), "status", replay.get().getStatus(), "problem_id", replay.get().getProblemId(), "idempotent_replay", true));
        }
        if (submissions.countByUser_IdAndStatusIn(user.getId(), PENDING_STATES) >= 3) {
            return ResponseEntity.status(429).body(Map.of("error", "最多同时处理 3 个提交"));
        }
        var problem = problems.findById(request.problemId()).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        String jobId = UUID.randomUUID().toString();
        Submission submission = submissions.save(Submission.create(user, problem, request.code(), request.language(), jobId, idempotencyKey));
        outboxes.save(SubmissionOutbox.of(submission));
        return ResponseEntity.status(201).body(Map.of("id", submission.getId(), "status", submission.getStatus(), "problem_id", submission.getProblemId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization);
        String key = "cache:submission:" + id;
        var cached = cache.get(key);
        if (cached.isPresent()) {
            Object owner = cached.get().get("user_id");
            if (owner == null || owner.toString().equals(user.getId().toString()) || "manager".equalsIgnoreCase(user.getRole())) {
                return ResponseEntity.ok(cached.get());
            }
        }
        return submissions.findById(id).filter(item -> item.getUserId().equals(user.getId()) || "manager".equalsIgnoreCase(user.getRole()))
                .<ResponseEntity<?>>map(item -> {
                    Map<String, Object> body = result(item);
                    if (isTerminal(item.getStatus())) cache.put(key, body);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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
        body.put("fail_testcase_index", item.getFailTestcaseIndex() != null ? item.getFailTestcaseIndex() : judgeResultView.failIndex(cases));
        body.put("compile_error", judgeResultView.compileError(item.getStatus()));
        body.put("created_at", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        return body;
    }

    private static boolean isTerminal(String status) {
        if (status == null) return false;
        return !List.of("Pending", "Queued", "Judging", "Claimed", "Compiling", "Compiled", "Running", "Checking").contains(status);
    }

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
    public record SubmissionRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("problem_id")
            @com.fasterxml.jackson.annotation.JsonAlias("problemId") @NotNull Integer problemId,
            @NotBlank @jakarta.validation.constraints.Size(max = 131072) String code,
            @NotBlank @jakarta.validation.constraints.Size(max = 50) String language) {}
}
