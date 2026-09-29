package com.xauat.oj.api.contest;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.domain.*;
import com.xauat.oj.core.contest.repository.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.xauat.oj.core.user.repository.UserRepository;
import java.util.UUID;

@RestController
@RequestMapping("/contests")
public class ContestOperationController {
    private final CurrentUser currentUser; private final ContestRepository contests; private final ContestAuditRepository audits;
    private final ContestEventRepository events; private final ContestClarificationRepository clarifications; private final RejudgeBatchRepository rejudges;
    private final ContestTeamRepository teams; private final ContestTeamMemberRepository members; private final ContestRoleRepository roles; private final ContestPackageRepository packages;
    private final ContestProblemRepository contestProblems; private final ContestSubmissionRepository contestSubmissions; private final ContestJudgeOutboxRepository judgeOutboxes; private final UserRepository users;
    private final ReferenceValidationJobRepository validationJobs;
    public ContestOperationController(CurrentUser currentUser, ContestRepository contests, ContestAuditRepository audits, ContestEventRepository events,
                                      ContestClarificationRepository clarifications, RejudgeBatchRepository rejudges, ContestTeamRepository teams, ContestTeamMemberRepository members,
                                      ContestRoleRepository roles, ContestPackageRepository packages, ContestProblemRepository contestProblems, ContestSubmissionRepository contestSubmissions, ContestJudgeOutboxRepository judgeOutboxes, UserRepository users, ReferenceValidationJobRepository validationJobs) { this.currentUser = currentUser; this.contests = contests; this.audits = audits; this.events = events; this.clarifications = clarifications; this.rejudges = rejudges; this.teams = teams; this.members = members; this.roles = roles; this.packages = packages; this.contestProblems = contestProblems; this.contestSubmissions = contestSubmissions; this.judgeOutboxes = judgeOutboxes; this.users = users; this.validationJobs = validationJobs; }

    @PostMapping("/{id}/thaw") @Transactional
    public ResponseEntity<?> thaw(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = manager(auth); var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); contest.thaw(); contests.save(contest); audits.save(ContestAudit.of(contest, user, "THAW", "manual thaw", "{}")); return ResponseEntity.ok(Map.of("id", id, "lifecycle_state", contest.getLifecycleState()));
    }

    @PostMapping("/{id}/finalize") @Transactional
    public ResponseEntity<?> finalizeContest(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = manager(auth); var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build();
        if (contest.getEndTime() != null && java.time.LocalDateTime.now().isBefore(contest.getEndTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛尚未结束"));
        long unfinished = contestSubmissions.countByContestIdAndStatusIn(id, java.util.List.of("Pending", "Judging"));
        if (unfinished > 0) return ResponseEntity.status(409).body(Map.of("error", "仍有未完成判题", "count", unfinished));
        if (rejudges.countByContestIdAndState(id, "PENDING") > 0) return ResponseEntity.status(409).body(Map.of("error", "存在待审核重判批次"));
        contest.finalizeContest(); contests.save(contest); audits.save(ContestAudit.of(contest, user, "FINALIZE", "manual finalize", "{}")); return ResponseEntity.ok(Map.of("id", id, "lifecycle_state", contest.getLifecycleState(), "final_revision", contest.getFinalRevision()));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<?> health(@PathVariable Integer id) { var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(Map.of("contest_id", id, "lifecycle_state", contest.getLifecycleState(), "healthy", true)); }

    @GetMapping("/{id}/events")
    public ResponseEntity<?> eventList(@PathVariable Integer id, @RequestParam(required=false, defaultValue="0") Integer after) { if (contest(id) == null) return ResponseEntity.notFound().build(); var items = after != null && after > 0 ? events.findTop100ByContestIdAndIdGreaterThanOrderByIdAsc(id, after) : events.findByContestIdOrderByIdDesc(id).stream().limit(100).toList(); var data = items.stream().map(e -> Map.<String,Object>of("id", e.getId(), "kind", e.getKind(), "audience", e.getAudience(), "payload", e.getPayload())).toList(); int next = data.isEmpty() ? (after == null ? 0 : after) : ((Number) data.get(data.size() - 1).get("id")).intValue(); return ResponseEntity.ok(Map.of("items", data, "next_cursor", next, "has_more", data.size() == 100)); }

    @GetMapping("/{id}/audit")
    public ResponseEntity<?> audit(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(audits.findByContestIdOrderByIdDesc(id).stream().map(a -> Map.of("id", a.getId(), "action", a.getAction(), "reason", a.getReason(), "payload", a.getPayload())).toList()); }

    @GetMapping("/{id}/clarifications")
    public ResponseEntity<?> clarificationList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(clarifications.findByContestIdOrderByIdDesc(id).stream().map(this::clarificationView).toList()); }

    @PostMapping("/{id}/clarifications") @Transactional
    public ResponseEntity<?> ask(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody ClarificationRequest request) { var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); var item = clarifications.save(ContestClarification.ask(contest, currentUser.require(auth), request.question())); return ResponseEntity.status(201).body(clarificationView(item)); }

    @PostMapping("/{id}/clarifications/{questionId}") @Transactional
    public ResponseEntity<?> answer(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer questionId, @Valid @RequestBody AnswerRequest request) { var user = manager(auth); var item = clarifications.findById(questionId).filter(q -> id.equals(q.getContestId())).orElse(null); if (item == null) return ResponseEntity.notFound().build(); item.answer(user.getId(), request.answer(), request.broadcast()); return ResponseEntity.ok(clarifications.save(item)); }

    @PostMapping("/{id}/rejudges") @Transactional
    public ResponseEntity<?> createRejudge(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody RejudgeRequest request) { var user = manager(auth); var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); var item = rejudges.save(RejudgeBatch.create(contest, user, request.reason())); audits.save(ContestAudit.of(contest, user, "REJUDGE_CREATE", request.reason(), "{}")); return ResponseEntity.status(201).body(Map.of("batch_id", item.getId(), "state", item.getState())); }

    @GetMapping("/{id}/rejudges") public ResponseEntity<?> rejudgeList(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(rejudges.findByContestIdOrderByIdDesc(id).stream().map(x -> Map.of("id", x.getId(), "state", x.getState())).toList()); }

    @GetMapping("/{id}/rejudges/{batchId}") public ResponseEntity<?> rejudgeDetail(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer batchId) { manager(auth); return rejudges.findById(batchId).filter(x -> id.equals(x.getContestId())).<ResponseEntity<?>>map(x -> ResponseEntity.ok(Map.of("id", x.getId(), "state", x.getState()))).orElseGet(() -> ResponseEntity.notFound().build()); }

    @PostMapping("/{id}/rejudges/{batchId}") @Transactional
    public ResponseEntity<?> reviewRejudge(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer batchId, @RequestBody ReviewRequest request) { var user = manager(auth); var item = rejudges.findById(batchId).filter(x -> id.equals(x.getContestId())).orElse(null); if (item == null) return ResponseEntity.notFound().build(); if (!"PENDING".equals(item.getState())) return ResponseEntity.status(409).body(Map.of("error", "重判批次已经审核")); item.review(user.getId(), request.approve()); if (item.approved()) { var candidates = contestSubmissions.findByContestIdAndRejudgeOfIsNullAndStatusIn(id, java.util.List.of("Accepted", "Wrong Answer", "Compilation Error", "Runtime Error", "Time Limit Exceeded")); for (var source : candidates) { var clone = contestSubmissions.save(ContestSubmission.rejudgeOf(source, UUID.randomUUID().toString(), "rejudge:" + batchId + ":" + source.getId())); judgeOutboxes.save(ContestJudgeOutbox.of(clone)); } } rejudges.save(item); return ResponseEntity.ok(Map.of("id", item.getId(), "state", item.getState())); }

    @PostMapping("/{id}/teams") @Transactional
    public ResponseEntity<?> createTeam(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody TeamRequest request) { var user = currentUser.require(auth); var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); var team = teams.save(ContestTeam.create(contest, user, request.name())); members.save(ContestTeamMember.create(contest, team, user)); return ResponseEntity.status(201).body(Map.of("id", team.getId(), "name", team.getName())); }

    @GetMapping("/{id}/teams") public ResponseEntity<?> teamList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(teams.findByContestIdOrderByIdAsc(id).stream().map(t -> Map.of("id", t.getId(), "name", t.getName())).toList()); }

    @PostMapping("/{id}/roles") @Transactional
    public ResponseEntity<?> assignRole(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody RoleRequest request) { manager(auth); var contest = contest(id); var user = users.findById(request.userId()).orElse(null); if (contest == null || user == null) return ResponseEntity.notFound().build(); var role = roles.save(ContestRole.create(contest, user, request.role())); return ResponseEntity.status(201).body(Map.of("user_id", role.getUserId(), "role", role.getRole())); }

    @GetMapping("/{id}/roles") public ResponseEntity<?> roleList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(roles.findByContestIdOrderByIdAsc(id).stream().map(r -> Map.of("user_id", r.getUserId(), "role", r.getRole())).toList()); }

    @PostMapping("/{id}/submissions/{submissionId}/override") @Transactional
    public ResponseEntity<?> override(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer submissionId, @Valid @RequestBody OverrideRequest request) { var user = manager(auth); var item = contestSubmissions.findById(submissionId).filter(s -> id.equals(s.getContestId())).orElse(null); if (item == null) return ResponseEntity.notFound().build(); item.overrideVerdict(request.verdict()); contestSubmissions.save(item); audits.save(ContestAudit.of(contest(id), user, "OVERRIDE", request.reason(), "{\"submission_id\":" + submissionId + "}")); return ResponseEntity.ok(Map.of("submission_id", submissionId, "verdict", request.verdict())); }

    @PostMapping("/{id}/problems/{problemId}/packages") @Transactional
    public ResponseEntity<?> uploadPackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer problemId, @Valid @RequestBody PackageRequest request) { var user = manager(auth); var problem = contestProblems.findByIdAndContestId(problemId, id).orElse(null); if (problem == null) return ResponseEntity.notFound().build(); String digest = sha256(request.payload()); if (request.digest() != null && !request.digest().equals(digest)) return ResponseEntity.badRequest().body(Map.of("error", "digest 不匹配")); var item = packages.save(ContestPackage.create(digest, problem, user.getId(), request.payload())); problem.attachPackage(digest); problem.markValidated(); contestProblems.save(problem); return ResponseEntity.status(201).body(Map.of("digest", item.getDigest(), "validation_state", item.getValidationState(), "problem_id", problemId)); }

    @GetMapping("/{id}/packages/{digest}") public ResponseEntity<?> getPackage(@PathVariable Integer id, @PathVariable String digest) { return packages.findById(digest).filter(p -> contestProblems.findByIdAndContestId(p.getProblemId(), id).isPresent()).<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of("digest", p.getDigest(), "problem_id", p.getProblemId(), "payload", p.getPayload(), "validation_state", p.getValidationState()))).orElseGet(() -> ResponseEntity.notFound().build()); }

    @PostMapping("/{id}/packages/{digest}") @Transactional
    public ResponseEntity<?> validatePackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable String digest, @Valid @RequestBody PackageUpdate request) { var user = manager(auth); var item = packages.findById(digest).orElse(null); var problem = item == null ? null : contestProblems.findByIdAndContestId(item.getProblemId(), id).orElse(null); if (item == null || problem == null) return ResponseEntity.notFound().build(); if (!digest.equals(sha256(request.payload()))) return ResponseEntity.badRequest().body(Map.of("error", "digest 不匹配")); item.replacePayload(request.payload(), user.getId()); problem.attachPackage(digest); problem.markValidated(); packages.save(item); contestProblems.save(problem); return ResponseEntity.ok(Map.of("digest", digest, "validation_state", item.getValidationState())); }

    @PostMapping("/{id}/rules") @Transactional
    public ResponseEntity<?> updateRules(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @RequestBody RulesRequest request) {
        var user = manager(auth); var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build();
        contest.updateRules(request.rulesVersion(), request.allowedLanguages(), request.penaltyTime(), request.activeSubmissionLimit(), request.freezeTime()); contests.save(contest);
        audits.save(ContestAudit.of(contest, user, "RULES_UPDATE", "contest rules updated", "{}"));
        return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime()));
    }

    @GetMapping("/{id}/rules")
    public ResponseEntity<?> rules(@PathVariable Integer id) { var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "contest_type", contest.getContestType(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime())); }

    private Contest contest(Integer id) { return contests.findById(id).orElse(null); }
    private com.xauat.oj.core.user.domain.User manager(String auth) { var user = currentUser.require(auth); if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); return user; }
    private Map<String,Object> clarificationView(ContestClarification item) { Map<String,Object> value = new HashMap<>(); value.put("id", item.getId()); value.put("question", item.getQuestion()); value.put("answer", item.getAnswer() == null ? "" : item.getAnswer()); return value; }
    public record ClarificationRequest(@NotBlank String question) {}
    public record AnswerRequest(@NotBlank String answer, boolean broadcast) {}
    public record RejudgeRequest(@NotBlank String reason) {}
    public record ReviewRequest(boolean approve) {}
    public record TeamRequest(@NotBlank String name) {}
    public record RoleRequest(Integer userId, @NotBlank String role) {}
    public record OverrideRequest(@NotBlank String verdict, @NotBlank String reason) {}
    public record PackageRequest(String digest, @NotBlank String payload) {}
    public record PackageUpdate(@NotBlank String payload) {}
    public record RulesRequest(String rulesVersion, String allowedLanguages, Integer penaltyTime, Integer activeSubmissionLimit, java.time.LocalDateTime freezeTime) {}
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
