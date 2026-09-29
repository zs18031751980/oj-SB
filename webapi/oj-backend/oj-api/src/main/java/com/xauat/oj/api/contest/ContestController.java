package com.xauat.oj.api.contest;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestParticipant;
import com.xauat.oj.core.contest.domain.ContestScoreboardSnapshot;
import com.xauat.oj.core.contest.repository.ContestParticipantRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.repository.ContestScoreboardSnapshotRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import com.xauat.oj.core.user.domain.User;

@RestController
@RequestMapping("/contests")
public class ContestController {
    private final CurrentUser currentUser; private final ContestRepository contests; private final ContestParticipantRepository participants; private final ContestProblemRepository problems;
    private final ContestSubmissionRepository submissions; private final ContestJudgeOutboxRepository outboxes;
    private final ContestScoreboardSnapshotRepository snapshots; private final ObjectMapper mapper; private final UserRepository users;
    public ContestController(CurrentUser currentUser, ContestRepository contests, ContestParticipantRepository participants, ContestProblemRepository problems,
                             ContestSubmissionRepository submissions, ContestJudgeOutboxRepository outboxes, ContestScoreboardSnapshotRepository snapshots, ObjectMapper mapper, UserRepository users) {
        this.currentUser = currentUser; this.contests = contests; this.participants = participants; this.problems = problems; this.submissions = submissions; this.outboxes = outboxes; this.snapshots = snapshots; this.mapper = mapper; this.users = users;
    }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> list() { return contests.findAll().stream().map(this::view).toList(); }

    @PostMapping({"", "/"})
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody ContestRequest request) {
        var user = currentUser.require(authorization); if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        Contest contest = contests.save(Contest.create(request.title(), request.description(), request.contestType(), request.startTime(), request.endTime(), user.getId())); return ResponseEntity.status(201).body(view(contest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Integer id) { return contests.findById(id).<ResponseEntity<?>>map(item -> ResponseEntity.ok(view(item))).orElseGet(() -> ResponseEntity.notFound().build()); }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id,
                                    @Valid @RequestBody ContestRequest request) {
        requireManager(authorization); var item = contests.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        item.updateDetails(request.title(), request.description(), request.contestType(), request.startTime(), request.endTime()); return ResponseEntity.ok(view(contests.save(item)));
    }

    @GetMapping("/manage")
    public ResponseEntity<?> manage(@RequestHeader(value = "Authorization", required = false) String authorization) { requireManager(authorization); return ResponseEntity.ok(contests.findAll().stream().map(this::view).toList()); }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) { var user = currentUser.require(authorization); if (!"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足")); if (!contests.existsById(id)) return ResponseEntity.notFound().build(); contests.deleteById(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/publish")
    @Transactional
    public ResponseEntity<?> publish(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) { requireManager(authorization); var item = contests.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build(); if (item.getStartTime() == null || item.getEndTime() == null || !item.getStartTime().isBefore(item.getEndTime())) return ResponseEntity.badRequest().body(Map.of("error", "比赛时间范围无效")); var contestProblems = problems.findByContestIdOrderBySortOrderAscIdAsc(id); if (contestProblems.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "比赛至少需要一道题")); var invalid = contestProblems.stream().filter(p -> !"VALID".equalsIgnoreCase(getValidationStatus(p))).findFirst(); if (invalid.isPresent()) return ResponseEntity.status(409).body(Map.of("error", "存在未通过校验的题目", "problem_id", invalid.get().getId())); item.publish(); item.requestScoreboardRefresh(); return ResponseEntity.ok(view(contests.save(item))); }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancel(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) { requireManager(authorization); var item = contests.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build(); item.cancel(); return ResponseEntity.ok(view(contests.save(item))); }

    @PostMapping("/{id}/join")
    @Transactional
    public ResponseEntity<?> join(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) { var user = currentUser.require(authorization); var contest = contests.findById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build(); if (!"SCHEDULED".equals(contest.getLifecycleState()) && !"READY".equals(contest.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛当前不可报名")); if (contest.getStartTime() != null && !LocalDateTime.now().isBefore(contest.getStartTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛已经开始")); boolean already = participants.findByContestIdAndUserId(id, user.getId()).isPresent(); if (!already) { participants.save(ContestParticipant.join(contest, user)); contest.requestScoreboardRefresh(); contests.save(contest); } return ResponseEntity.status(already ? 200 : 201).body(Map.of("contest_id", id, "joined", true, "already_joined", already)); }

    @GetMapping("/{id}/problems")
    public ResponseEntity<?> problemList(@PathVariable Integer id) { if (!contests.existsById(id)) return ResponseEntity.notFound().build(); return ResponseEntity.ok(problems.findByContestIdOrderBySortOrderAscIdAsc(id).stream().map(p -> Map.of("id", p.getId(), "problem_index", p.getProblemIndex(), "title", p.getTitle())).toList()); }

    @GetMapping("/{id}/problems/{problemId}")
    public ResponseEntity<?> problem(@PathVariable Integer id, @PathVariable Integer problemId) {
        return problems.findByIdAndContestId(problemId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of("id", item.getId(), "problem_index", item.getProblemIndex(), "title", item.getTitle())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/problems/{problemId}")
    public ResponseEntity<?> problemGlobal(@PathVariable Integer problemId) { return problems.findById(problemId).<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of("id", p.getId(), "problem_index", p.getProblemIndex(), "title", p.getTitle()))).orElseGet(() -> ResponseEntity.notFound().build()); }

    @PostMapping("/{id}/problems/{problemId}/submit")
    @Transactional
    public ResponseEntity<?> submit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @PathVariable Integer id, @PathVariable Integer problemId, @Valid @RequestBody SubmitRequest request) {
        var user = currentUser.require(authorization);
        var contest = contests.findById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build(); if (!"SCHEDULED".equals(contest.getLifecycleState()) && !"RUNNING".equals(contest.getLifecycleState()) && !"FROZEN".equals(contest.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛当前不可提交")); if (contest.getStartTime() != null && LocalDateTime.now().isBefore(contest.getStartTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛尚未开始")); if (contest.getEndTime() != null && !LocalDateTime.now().isBefore(contest.getEndTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛已经结束"));
        if (participants.findByContestIdAndUserId(id, user.getId()).isEmpty()) return ResponseEntity.status(403).body(Map.of("error", "请先报名比赛"));
        var problem = problems.findByIdAndContestId(problemId, id).orElse(null); if (problem == null) return ResponseEntity.notFound().build();
        if (idempotencyKey != null) {
            var replay = submissions.findByContestIdAndUserIdAndIdempotencyKey(id, user.getId(), idempotencyKey);
            if (replay.isPresent()) return ResponseEntity.status(201).body(submissionView(replay.get(), true));
        }
        String jobId = UUID.randomUUID().toString();
        var submission = submissions.save(com.xauat.oj.core.contest.domain.ContestSubmission.create(contest, user, problem, request.code(), request.language(), jobId, idempotencyKey));
        outboxes.save(com.xauat.oj.core.contest.domain.ContestJudgeOutbox.of(submission));
        return ResponseEntity.status(201).body(submissionView(submission, false));
    }

    @GetMapping("/{id}/problems/{problemId}/submission/{submissionId}")
    public ResponseEntity<?> submission(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id,
                                        @PathVariable Integer problemId, @PathVariable Integer submissionId) {
        var user = currentUser.require(authorization);
        return submissions.findById(submissionId).filter(s -> id.equals(s.getContestId()) && user.getId().equals(s.getUserId()))
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(submissionView(s, false))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/statuses")
    public ResponseEntity<?> statuses(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        if (!contests.existsById(id)) return ResponseEntity.notFound().build();
        var user = currentUser.require(authorization);
        var items = submissions.findByContestIdAndUserIdOrderByIdDesc(id, user.getId());
        java.util.LinkedHashMap<String, String> latest = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Boolean> anyAccepted = new java.util.LinkedHashMap<>();
        for (var s : items) {
            latest.putIfAbsent(s.getProblemIndex(), s.getStatus() == null ? "" : s.getStatus());
            if ("Accepted".equalsIgnoreCase(s.getVerdict()) || "Accepted".equalsIgnoreCase(s.getStatus())) anyAccepted.put(s.getProblemIndex(), true);
        }
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        latest.forEach((index, status) -> body.put(index, Map.of("status", status, "solved", anyAccepted.getOrDefault(index, false))));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/rankings")
    public ResponseEntity<?> rankings(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable Integer id, @RequestHeader(value="If-None-Match", required=false) String ifNoneMatch) {
        var contest = contests.findById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build();
        String kind = snapshotKind(contest, authorization);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("mode", "ACM".equalsIgnoreCase(contest.getContestType()) ? "acm" : "oi");
        body.put("contest_type", contest.getContestType() == null ? "" : contest.getContestType());
        body.put("problem_indexes", problems.findByContestIdOrderBySortOrderAscIdAsc(id).stream().map(com.xauat.oj.core.contest.domain.ContestProblem::getProblemIndex).toList());
        body.put("rankings", scoreboardRows(contest, kind));
        try { String payload = mapper.writeValueAsString(body); var snapshot = snapshots.findByContestIdAndSnapshotKind(id, kind).orElseGet(() -> ContestScoreboardSnapshot.create(contest, kind, payload, 1)); if ("FINAL".equals(kind) && !payload.equals(snapshot.getPayload())) return snapshotResponse(snapshot, ifNoneMatch, body); if (!payload.equals(snapshot.getPayload())) snapshot.replace(payload, snapshot.getScoreboardVersion() + 1); snapshots.save(snapshot); return snapshotResponse(snapshot, ifNoneMatch, body); } catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("error", "排行榜生成失败")); }
    }

    private ResponseEntity<?> snapshotResponse(ContestScoreboardSnapshot snapshot, String ifNoneMatch, Object body) { String etag = "\"" + sha256(snapshot.getPayload()) + "\""; if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).header(HttpHeaders.ETAG, etag).build(); return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).header("X-Scoreboard-Version", String.valueOf(snapshot.getScoreboardVersion())).body(body); }
    private String snapshotKind(Contest contest, String authorization) { if ("FINALIZED".equals(contest.getLifecycleState())) return "FINAL"; boolean jury = false; if (authorization != null && !authorization.isBlank()) { try { var user = currentUser.require(authorization); jury = "manager".equals(user.getRole()) || "staff".equals(user.getRole()); } catch (RuntimeException ignored) {} } return contest.getFreezeTime() != null && java.time.LocalDateTime.now().isAfter(contest.getFreezeTime()) && !jury ? "PUBLIC_FREEZE" : "LIVE"; }
    private List<Map<String,Object>> scoreboardRows(Contest contest, String kind) {
        var source = submissions.findByContestIdOrderByIdDesc(contest.getId()).stream().sorted(java.util.Comparator.comparing(com.xauat.oj.core.contest.domain.ContestSubmission::getId)).filter(s -> s.getContestId().equals(contest.getId()) && s.getStatus() != null && !"Pending".equals(s.getStatus()) && !"Judging".equals(s.getStatus()) && ("LIVE".equals(kind) || s.getSubmittedAt() == null || contest.getFreezeTime() == null || s.getSubmittedAt().isBefore(contest.getFreezeTime()))).toList();
        Map<Integer, java.util.LinkedHashMap<String, ProblemScore>> perUser = new java.util.HashMap<>();
        participants.findAll().stream().filter(p -> contest.getId().equals(p.getContestId())).forEach(p -> perUser.putIfAbsent(p.getUserId(), new java.util.LinkedHashMap<>()));
        for (var submission : source) { var byProblem = perUser.computeIfAbsent(submission.getUserId(), ignored -> new java.util.LinkedHashMap<>()); byProblem.computeIfAbsent(submission.getProblemIndex(), ProblemScore::new).accept(submission, contest.getStartTime(), "ACM".equalsIgnoreCase(contest.getContestType()), contest.getPenaltyTime()); }
        var rows = perUser.entrySet().stream().map(entry -> { int solved = entry.getValue().values().stream().mapToInt(ProblemScore::solved).sum(); int score = "OI".equalsIgnoreCase(contest.getContestType()) ? entry.getValue().values().stream().mapToInt(ProblemScore::oiScore).sum() : solved; int penalty = entry.getValue().values().stream().mapToInt(ProblemScore::penalty).sum(); java.util.List<Map<String,Object>> problems = entry.getValue().values().stream().map(ProblemScore::asMap).toList(); return new Row(entry.getKey(), score, solved, penalty, problems); }).sorted(java.util.Comparator.comparingInt(Row::score).reversed().thenComparingInt(Row::penalty).thenComparingInt(Row::userId)).toList();
        List<Map<String,Object>> result = new java.util.ArrayList<>(); int rank = 0; int previousScore = Integer.MIN_VALUE; for (int i = 0; i < rows.size(); i++) { var row = rows.get(i); if (row.score() != previousScore) rank = i + 1; previousScore = row.score(); User user = users.findById(row.userId()).orElse(null); result.add(Map.of("rank", rank, "user_id", row.userId(), "username", user == null ? "" : (user.getUsername() == null ? "" : user.getUsername()), "avatar_url", user == null ? "" : (user.getAvatarUrl() == null ? "" : user.getAvatarUrl()), "solved_count", row.solved(), "penalty", row.penalty(), "score", row.score(), "problems", row.problems())); } return result;
    }
    private static final class ProblemScore {
        private final String index; private boolean solved; private int submissions; private int penalty; private int best; private int passed; private int total; private String status; private Integer solveMinutes;
        ProblemScore(String index) { this.index = index; }
        void accept(com.xauat.oj.core.contest.domain.ContestSubmission s, LocalDateTime start, boolean acm, int penaltyMinutes) {
            submissions++; int pass = s.getTotal() == 0 ? 0 : Math.max(0, s.getPassed()); int tot = Math.max(0, s.getTotal()); passed = Math.max(passed, pass); total = Math.max(total, tot); status = (s.getVerdict() == null || s.getVerdict().isBlank()) ? s.getStatus() : s.getVerdict();
            int percent = tot == 0 ? 0 : (s.getScore() > 0 ? s.getScore() : pass * 100 / tot);
            if (solved) return;
            if ("Accepted".equalsIgnoreCase(s.getVerdict()) || "Accepted".equalsIgnoreCase(s.getStatus())) { solved = true; int minutes = start == null || s.getSubmittedAt() == null ? 0 : (int) java.time.Duration.between(start, s.getSubmittedAt()).toMinutes(); solveMinutes = minutes; penalty = acm ? minutes + (submissions - 1) * penaltyMinutes : 0; best = Math.max(best, percent); }
            else { best = Math.max(best, percent); }
        }
        int solved() { return solved ? 1 : 0; } int penalty() { return penalty; } int oiScore() { return best; }
        Map<String,Object> asMap() { var m = new java.util.HashMap<String,Object>(); m.put("problem_index", index); m.put("solved", solved); m.put("passed", passed); m.put("total", total); m.put("score", best); m.put("status", status == null ? "" : status); m.put("submissions", submissions); m.put("solve_minutes", solveMinutes); return m; }
    }
    private record Row(Integer userId, int score, int solved, int penalty, java.util.List<Map<String,Object>> problems) {}

    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String getValidationStatus(com.xauat.oj.core.contest.domain.ContestProblem problem) { return problem.getValidationStatus(); }

    private void requireManager(String authorization) { var user = currentUser.require(authorization); if (!"manager".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); }
    private Map<String, Object> view(Contest item) { return Map.of("id", item.getId(), "title", item.getTitle(), "description", item.getDescription(), "contest_type", item.getContestType(), "lifecycle_state", item.getLifecycleState(), "start_time", item.getStartTime() == null ? "" : item.getStartTime(), "end_time", item.getEndTime() == null ? "" : item.getEndTime(), "participants_count", participants.countByContestId(item.getId())); }
    private Map<String, Object> submissionView(com.xauat.oj.core.contest.domain.ContestSubmission item, boolean replay) { return Map.of("id", item.getId(), "contest_id", item.getContestId(), "problem_index", item.getProblemIndex(), "status", item.getStatus(), "verdict", item.getVerdict() == null ? "" : item.getVerdict(), "job_id", item.getJobId(), "idempotent_replay", replay); }
    public record ContestRequest(@NotBlank String title, @NotBlank String description, String contestType, LocalDateTime startTime, LocalDateTime endTime) {}
    public record SubmitRequest(@NotBlank String code, @NotBlank String language) {}
}
