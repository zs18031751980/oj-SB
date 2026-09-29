package com.xauat.oj.api.problem;

import com.xauat.oj.core.problem.domain.Problem;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.repository.*;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/problems")
public class ProblemController {
    private final ProblemRepository problems;
    private final CurrentUser currentUser; private final ContestProblemRepository contestProblems; private final ContestRepository contests;
    private final ContestSubmissionRepository submissions; private final ContestJudgeOutboxRepository outboxes;

    public ProblemController(ProblemRepository problems, CurrentUser currentUser, ContestProblemRepository contestProblems, ContestRepository contests, ContestSubmissionRepository submissions, ContestJudgeOutboxRepository outboxes) { this.problems = problems; this.currentUser = currentUser; this.contestProblems = contestProblems; this.contests = contests; this.submissions = submissions; this.outboxes = outboxes; }

    @GetMapping({"", "/"})
    public Map<String, Object> list() {
        List<Map<String, Object>> data = problems.findAll().stream().map(this::summary).toList();
        return Map.of("data", data, "total", data.size());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Integer id) {
        return problems.findById(id).<ResponseEntity<?>>map(problem -> ResponseEntity.ok(detailOf(problem)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/library/submit") @Transactional
    public ResponseEntity<?> librarySubmit(@RequestHeader(value="Authorization", required=false) String auth, @RequestHeader(value="Idempotency-Key", required=false) String key, @Valid @RequestBody LibraryRequest request) {
        var user = currentUser.require(auth); var problem = contestProblems.findById(request.contestProblemId()).orElse(null); if (problem == null) return ResponseEntity.notFound().build(); var contest = contests.findById(problem.getContestId()).orElse(null); if (contest == null || contest.getEndTime() == null || contest.getEndTime().isAfter(LocalDateTime.now())) return ResponseEntity.notFound().build();
        if (key != null && key.length() > 128) return ResponseEntity.badRequest().body(Map.of("error", "幂等键过长"));
        if (key != null) { var replay = submissions.findByContestIdAndUserIdAndIdempotencyKey(contest.getId(), user.getId(), "library:" + key); if (replay.isPresent()) return ResponseEntity.ok(Map.of("submission_id", replay.get().getId(), "status", replay.get().getStatus())); }
        var item = submissions.save(ContestSubmission.create(contest, user, problem, request.code(), request.language(), UUID.randomUUID().toString(), key == null ? null : "library:" + key)); outboxes.save(ContestJudgeOutbox.of(item)); return ResponseEntity.status(201).body(Map.of("submission_id", item.getId(), "status", item.getStatus()));
    }

    public record LibraryRequest(Integer contestProblemId, @NotBlank String code, @NotBlank String language) {}

    @GetMapping("/library/submission/{id}")
    public ResponseEntity<?> librarySubmission(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = currentUser.require(auth); return submissions.findById(id).filter(s -> user.getId().equals(s.getUserId())).<ResponseEntity<?>>map(s -> ResponseEntity.ok(Map.of("submission_id", s.getId(), "status", s.getStatus(), "verdict", s.getVerdict() == null ? "" : s.getVerdict()))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> summary(Problem problem) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", problem.getId()); m.put("sourceNumber", ""); m.put("category", ""); m.put("categoryLabel", "");
        m.put("title", problem.getTitle()); m.put("difficulty", problem.getDifficulty());
        m.put("tags", List.of()); m.put("timeLimit", problem.getTimeLimit()); m.put("memoryLimit", problem.getMemoryLimit());
        m.put("interactive", false); m.put("judgeable", false); m.put("accepted_count", 0); m.put("submission_count", 0);
        return m;
    }

    private Map<String, Object> detailOf(Problem problem) {
        return Map.of("id", problem.getId(), "title", problem.getTitle(), "description", problem.getDescription(),
                "inputFormat", problem.getInputDesc(), "outputFormat", problem.getOutputDesc(),
                "difficulty", problem.getDifficulty(), "timeLimit", problem.getTimeLimit(), "memoryLimit", problem.getMemoryLimit());
    }
}
