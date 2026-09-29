package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin/contests")
public class AdminContestController {
    private final CurrentUser currentUser; private final ContestRepository contests; private final ContestProblemRepository problems; private final ReferenceValidationJobRepository validationJobs;
    public AdminContestController(CurrentUser currentUser, ContestRepository contests, ContestProblemRepository problems, ReferenceValidationJobRepository validationJobs) { this.currentUser = currentUser; this.contests = contests; this.problems = problems; this.validationJobs = validationJobs; }
    @GetMapping({"", "/"}) public ResponseEntity<?> list(@RequestHeader(value="Authorization", required=false) String auth) { manager(auth); return ResponseEntity.ok(contests.findAll().stream().map(this::view).toList()); }
    @GetMapping("/{id}") public ResponseEntity<?> detail(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); return contests.findById(id).<ResponseEntity<?>>map(c -> ResponseEntity.ok(view(c))).orElseGet(() -> ResponseEntity.notFound().build()); }
    @PostMapping({"", "/"}) @Transactional public ResponseEntity<?> create(@RequestHeader(value="Authorization", required=false) String auth, @Valid @RequestBody Request request) { var user = manager(auth); return ResponseEntity.status(201).body(view(contests.save(Contest.create(request.title(), request.description(), request.contestType(), request.startTime(), request.endTime(), user.getId())))); }
    @PutMapping("/{id}") @Transactional public ResponseEntity<?> update(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody Request request) { manager(auth); var c = contests.findById(id).orElse(null); if (c == null) return ResponseEntity.notFound().build(); c.updateDetails(request.title(), request.description(), request.contestType(), request.startTime(), request.endTime()); return ResponseEntity.ok(view(contests.save(c))); }
    @DeleteMapping("/{id}") @Transactional public ResponseEntity<?> delete(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (!contests.existsById(id)) return ResponseEntity.notFound().build(); contests.deleteById(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/regenerate-testcases") @Transactional
    public ResponseEntity<?> regenerate(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        manager(auth); if (!contests.existsById(id)) return ResponseEntity.notFound().build();
        var jobs = problems.findByContestIdOrderBySortOrderAscIdAsc(id).stream().map(problem -> {
            var job = ReferenceValidationJob.create(java.util.UUID.randomUUID().toString(), problem); job.markQueued(); return validationJobs.save(job);
        }).toList();
        return ResponseEntity.accepted().body(java.util.Map.of("contest_id", id, "state", "QUEUED", "jobs", jobs.stream().map(ReferenceValidationJob::getId).toList()));
    }

    @GetMapping("/{id}/testcase-generation")
    public ResponseEntity<?> generation(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        manager(auth); if (!contests.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(problems.findByContestIdOrderBySortOrderAscIdAsc(id).stream().flatMap(problem -> validationJobs.findByProblemIdOrderByCreatedAtDesc(problem.getId()).stream().limit(1)).map(job -> java.util.Map.of("job_id", job.getId(), "problem_id", job.getProblemId(), "version", job.getVersion(), "state", job.getState())).toList());
    }
    private com.xauat.oj.core.user.domain.User manager(String auth) { var u = currentUser.require(auth); if (!"manager".equals(u.getRole()) && !"staff".equals(u.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); return u; }
    private Map<String,Object> view(Contest c) { return Map.of("id", c.getId(), "title", c.getTitle(), "description", c.getDescription(), "contest_type", c.getContestType(), "lifecycle_state", c.getLifecycleState()); }
    public record Request(@NotBlank String title, @NotBlank String description, String contestType, LocalDateTime startTime, LocalDateTime endTime) {}
}
