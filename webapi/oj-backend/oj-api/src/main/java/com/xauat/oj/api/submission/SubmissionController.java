package com.xauat.oj.api.submission;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import com.xauat.oj.core.submission.domain.Submission;
import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {
    private final CurrentUser currentUser; private final ProblemRepository problems; private final SubmissionRepository submissions;
    private final SubmissionOutboxRepository outboxes;

    public SubmissionController(CurrentUser currentUser, ProblemRepository problems, SubmissionRepository submissions,
                                SubmissionOutboxRepository outboxes) {
        this.currentUser = currentUser; this.problems = problems; this.submissions = submissions; this.outboxes = outboxes;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer per_page) {
        var all = submissions.findByUserIdOrderByIdDesc(currentUser.require(authorization).getId()); long total = all.size();
        int safePage = Math.max(1, page); int size = Math.max(1, Math.min(per_page, 200));
        int from = Math.min((int) total, (safePage - 1) * size); int to = Math.min((int) total, from + size);
        List<Map<String, Object>> data = all.subList(from, to).stream().map(this::historyItem).toList();
        return Map.of("total", total, "page", safePage, "per_page", size, "data", data);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @Valid @RequestBody SubmissionRequest request) {
        var user = currentUser.require(authorization);
        if (idempotencyKey != null && idempotencyKey.length() > 128) return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        if (idempotencyKey != null) {
            var replay = submissions.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
            if (replay.isPresent()) return ResponseEntity.status(201).body(result(replay.get(), true));
        }
        var problem = problems.findById(request.problemId()).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        String jobId = UUID.randomUUID().toString();
        Submission submission = submissions.save(Submission.create(user, problem, request.code(), request.language(), jobId, idempotencyKey));
        outboxes.save(SubmissionOutbox.of(submission));
        return ResponseEntity.status(201).body(result(submission, false));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization);
        return submissions.findById(id).filter(item -> item.getUserId().equals(user.getId())).<ResponseEntity<?>>map(item -> ResponseEntity.ok(result(item, false)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> result(Submission item, boolean replay) { return Map.of("id", item.getId(), "problem_id", item.getProblemId(), "status", item.getStatus(), "job_id", item.getJobId(), "time_used", item.getTimeUsed() == null ? 0 : item.getTimeUsed(), "memory_used", item.getMemoryUsed() == null ? 0 : item.getMemoryUsed(), "testcase_results", item.getTestcaseResults() == null ? "[]" : item.getTestcaseResults(), "idempotent_replay", replay); }
    private Map<String, Object> historyItem(Submission item) {
        var problem = item.getProblem();
        return Map.of("id", item.getId(), "problem_id", item.getProblemId(), "problem_title", problem == null ? "" : problem.getTitle(),
                "difficulty", problem == null ? null : problem.getDifficulty(), "language", item.getLanguage(), "status", item.getStatus(),
                "time_used", item.getTimeUsed(), "created_at", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
    }
    public record SubmissionRequest(@NotNull Integer problemId, @NotBlank String code, @NotBlank String language) {}
}
