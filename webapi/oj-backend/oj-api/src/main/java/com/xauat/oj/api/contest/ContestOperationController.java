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
    private final ReferenceValidationJobRepository validationJobs; private final JudgementRepository judgements;
    private final ContestPermissionService permission; private final PackageService packageService; private final JuryReauthService reauth;
    private final ContestScoreboardService scoreboard; private final ContestParticipantRepository participants;
    private final ContestScoreboardSnapshotRepository snapshots;
    @org.springframework.beans.factory.annotation.Value("${oj.rejudge.dual-review-min:0}") private int dualReviewMin;
    public ContestOperationController(CurrentUser currentUser, ContestRepository contests, ContestAuditRepository audits, ContestEventRepository events,
                                      ContestClarificationRepository clarifications, RejudgeBatchRepository rejudges, ContestTeamRepository teams, ContestTeamMemberRepository members,
                                      ContestRoleRepository roles, ContestPackageRepository packages, ContestProblemRepository contestProblems, ContestSubmissionRepository contestSubmissions, ContestJudgeOutboxRepository judgeOutboxes, UserRepository users, ReferenceValidationJobRepository validationJobs, JudgementRepository judgements,
                                      ContestPermissionService permission, PackageService packageService, JuryReauthService reauth, ContestScoreboardService scoreboard,
                                      ContestParticipantRepository participants, ContestScoreboardSnapshotRepository snapshots) { this.currentUser = currentUser; this.contests = contests; this.audits = audits; this.events = events; this.clarifications = clarifications; this.rejudges = rejudges; this.teams = teams; this.members = members; this.roles = roles; this.packages = packages; this.contestProblems = contestProblems; this.contestSubmissions = contestSubmissions; this.judgeOutboxes = judgeOutboxes; this.users = users; this.validationJobs = validationJobs; this.judgements = judgements; this.permission = permission; this.packageService = packageService; this.reauth = reauth; this.scoreboard = scoreboard; this.participants = participants; this.snapshots = snapshots; }

    @PostMapping("/{id}/thaw") @Transactional
    public ResponseEntity<?> thaw(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = manager(auth); var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build(); contest.thaw(); contests.save(contest); audits.save(ContestAudit.of(contest, user, "THAW", "manual thaw", "{}")); return ResponseEntity.ok(Map.of("id", id, "lifecycle_state", contest.getLifecycleState()));
    }

    @PostMapping("/{id}/finalize") @Transactional
    public ResponseEntity<?> finalizeContest(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = requireCapability(id, auth, "control");
        var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        if ("FINALIZED".equals(contest.getLifecycleState())) {
            var existing = scoreboard.ensure(contest, scoreboard.kindFor(contest, false));
            return ResponseEntity.ok(scoreboard.bodyOf(existing));
        }
        if (java.util.List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "未发布或已取消比赛不能结算"));
        }
        if (contest.getEndTime() != null && java.time.LocalDateTime.now().isBefore(contest.getEndTime())) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛尚未结束，不能结算"));
        }
        long unresolved = contestSubmissions.findByContest_IdOrderByIdDesc(id).stream()
                .filter(com.xauat.oj.core.contest.domain.ContestSubmission::isContestEligible)
                .filter(s -> !VALID_REJUDGE_STATUSES.contains(s.getStatus())).count();
        if (unresolved > 0) return ResponseEntity.status(409).body(Map.of("error", "仍有判题任务未完成，不能结算", "count", unresolved));
        if (rejudges.countByContest_IdAndState(id, "PENDING") > 0) return ResponseEntity.status(409).body(Map.of("error", "存在待审核重判批次"));
        if (scoreboard.isFrozen(contest)) scoreboard.ensure(contest, ContestScoreboardService.PUBLIC_FREEZE);
        contest.finalizeContest();
        contests.save(contest);
        var snapshot = scoreboard.ensure(contest, scoreboard.kindFor(contest, false));
        audits.save(ContestAudit.of(contest, user, "FINALIZE", "审核并确认最终成绩", "{}"));
        return ResponseEntity.ok(scoreboard.bodyOf(snapshot));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<?> health(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build();
        var all = contestSubmissions.findByContest_IdOrderByIdDesc(id);
        long pending = all.stream().filter(s -> !VALID_REJUDGE_STATUSES.contains(s.getStatus())).count();
        long accepted = all.stream().filter(s -> "AC".equalsIgnoreCase(s.getVerdict()) || "AC".equalsIgnoreCase(s.getStatus())).count();
        var waitingItems = all.stream().filter(com.xauat.oj.core.contest.domain.ContestSubmission::isContestEligible)
                .filter(s -> "Pending".equals(s.getStatus()) || "Queued".equals(s.getStatus())).toList();
        long oldestWait = waitingItems.stream().map(com.xauat.oj.core.contest.domain.ContestSubmission::getReceivedAt)
                .filter(java.util.Objects::nonNull).mapToLong(t -> java.time.Duration.between(t, java.time.LocalDateTime.now()).getSeconds())
                .max().orElse(0L);
        long systemErrors = all.stream().filter(s -> "SystemError".equals(s.getStatus())).count();
        var live = snapshots.findByContest_IdAndSnapshotKind(id, ContestScoreboardService.LIVE).orElse(null);
        int liveVersion = live == null ? 0 : live.getScoreboardVersion();
        long lag = Math.max(0, contest.getScoreboardRequestedVersion() - liveVersion);
        long stale = lag > 0 && live != null && live.getUpdatedAt() != null
                ? Math.max(0, java.time.Duration.between(live.getUpdatedAt(), java.time.LocalDateTime.now()).getSeconds()) : 0;
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("contest_id", id);
        body.put("lifecycle_state", contest.getLifecycleState());
        body.put("healthy", pending == 0 && systemErrors == 0);
        body.put("participants", participants.countByContest_Id(id));
        body.put("problems", contestProblems.findByContest_IdOrderBySortOrderAscIdAsc(id).size());
        body.put("pending_submissions", pending);
        body.put("accepted_submissions", accepted);
        body.put("waiting", waitingItems.size());
        body.put("oldest_wait_seconds", (double) oldestWait);
        body.put("projection_version_lag", lag);
        body.put("projection_stale_seconds", (double) stale);
        body.put("unresolved_system_errors", systemErrors);
        body.put("scoreboard_version", contest.getScoreboardRequestedVersion());
        body.put("is_frozen", scoreboard.isFrozen(contest));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<?> eventList(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @RequestParam(required=false, defaultValue="0") Integer after) {
        if (contest(id) == null) return ResponseEntity.notFound().build();
        var user = currentUser.optional(auth);
        boolean jury = user != null && permission.allowed(id, user, "jury");
        Integer userId = user == null ? null : user.getId();
        boolean participant = userId != null && participants.findByContest_IdAndUser_Id(id, userId).isPresent();
        var raw = after != null && after > 0
                ? events.findTop100ByContest_IdAndIdGreaterThanOrderByIdAsc(id, after)
                : events.findByContest_IdOrderByIdDesc(id);
        var items = raw.stream()
                .filter(e -> "public".equals(e.getAudience())
                        || (jury)
                        || ("entry".equals(e.getAudience()) && userId != null && userId.equals(e.getRecipientId()))
                        || ("entry".equals(e.getAudience()) && participant && e.getRecipientId() == null))
                .sorted(java.util.Comparator.comparing(com.xauat.oj.core.contest.domain.ContestEvent::getId))
                .limit(100)
                .map(e -> {
                    Map<String, Object> view = new java.util.LinkedHashMap<>();
                    view.put("id", e.getId()); view.put("type", e.getKind()); view.put("audience", e.getAudience()); view.put("data", e.getPayload());
                    return view;
                }).toList();
        int next = items.isEmpty() ? (after == null ? 0 : after) : ((Number) items.get(items.size() - 1).get("id")).intValue();
        return ResponseEntity.ok(Map.of("items", items, "next_cursor", next, "has_more", items.size() == 100));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<?> audit(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(audits.findByContest_IdOrderByIdDesc(id).stream().map(a -> Map.of("id", a.getId(), "action", a.getAction(), "reason", a.getReason(), "payload", a.getPayload())).toList()); }

    @GetMapping("/{id}/clarifications")
    public ResponseEntity<?> clarificationList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(clarifications.findByContest_IdOrderByIdDesc(id).stream().map(this::clarificationView).toList()); }

    @PostMapping("/{id}/clarifications") @Transactional
    public ResponseEntity<?> ask(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody ClarificationRequest request) {
        var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        var user = currentUser.require(auth);
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isEmpty() && !permission.allowed(id, user, "jury")) {
            return ResponseEntity.status(403).body(Map.of("error", "需要比赛参赛身份"));
        }
        if (java.util.List.of("DRAFT", "READY", "CANCELLED", "FINALIZED").contains(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛暂不接受提问"));
        }
        var item = clarifications.save(ContestClarification.ask(contest, user, request.question()));
        return ResponseEntity.status(201).body(clarificationView(item));
    }

    @PostMapping("/{id}/clarifications/{questionId}") @Transactional
    public ResponseEntity<?> answer(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer questionId, @Valid @RequestBody AnswerRequest request) {
        var user = requireCapability(id, auth, "jury");
        var item = clarifications.findById(questionId).filter(q -> id.equals(q.getContestId())).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        if (item.getClaimedBy() != null && !item.getClaimedBy().equals(user.getId())) {
            return ResponseEntity.status(409).body(Map.of("error", "问题由另一裁判认领"));
        }
        item.answer(user.getId(), request.answer(), request.broadcast());
        clarifications.save(item);
        String audience = request.broadcast() ? "public" : "entry";
        events.save(ContestEvent.of(contest(id), "clarification", audience,
                "{\"id\":" + item.getId() + ",\"question\":" + json(item.getQuestion()) + ",\"answer\":" + json(item.getAnswer()) + "}"));
        audits.save(ContestAudit.of(contest(id), user, "clarification.answer", "裁判答疑",
                "{\"id\":" + item.getId() + ",\"broadcast\":" + request.broadcast() + "}"));
        return ResponseEntity.ok(clarificationView(item));
    }

    @PostMapping("/{id}/rejudges")
    @Transactional
    public ResponseEntity<?> createRejudge(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @RequestBody RejudgeRequest request) {
        var user = requireCapability(id, auth, "rejudge");
        var contest = lockContest(id);
        if (contest == null) return ResponseEntity.notFound().build();
        if (java.util.List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "当前比赛不可复判"));
        }
        var ids = request.submissionIds();
        if (ids == null || ids.isEmpty() || ids.size() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "复判每批需要 1 至 500 条提交"));
        }
        var originals = contestSubmissions.findAllById(new java.util.HashSet<>(ids)).stream()
                .filter(s -> id.equals(s.getContestId()) && s.isContestEligible()).toList();
        if (originals.size() != new java.util.HashSet<>(ids).size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "提交不存在或不属于正式比赛"));
        }
        if (originals.stream().anyMatch(s -> !VALID_REJUDGE_STATUSES.contains(s.getStatus()))) {
            return ResponseEntity.status(409).body(Map.of("error", "仍在判题的提交不能复判"));
        }
        var batch = rejudges.save(RejudgeBatch.create(contest, user, request.reason() == null ? "" : request.reason()));
        for (var source : originals) {
            var clone = ContestSubmission.rejudgeOf(source, UUID.randomUUID().toString(), "rejudge:" + batch.getId() + ":" + source.getId());
            contestSubmissions.save(clone);
            judgeOutboxes.save(ContestJudgeOutbox.of(clone));
        }
        audits.save(ContestAudit.of(contest, user, "REJUDGE_CREATE", request.reason(), "{\"batch_id\":" + batch.getId() + "}"));
        return ResponseEntity.status(202).body(Map.of("batch_id", batch.getId(), "state", batch.getState()));
    }

    @GetMapping("/{id}/rejudges") public ResponseEntity<?> rejudgeList(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(rejudges.findByContest_IdOrderByIdDesc(id).stream().map(x -> Map.of("id", x.getId(), "state", x.getState())).toList()); }

    @GetMapping("/{id}/rejudges/{batchId}")
    public ResponseEntity<?> rejudgeDetail(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer batchId) {
        manager(auth);
        var batch = rejudges.findById(batchId).filter(x -> id.equals(x.getContestId())).orElse(null);
        if (batch == null) return ResponseEntity.notFound().build();
        var candidates = contestSubmissions.findByIdempotencyKeyStartingWith("rejudge:" + batchId + ":");
        var changes = candidates.stream().map(c -> {
            var original = c.getRejudgeOfId() == null ? null : contestSubmissions.findById(c.getRejudgeOfId()).orElse(null);
            Map<String, Object> change = new java.util.LinkedHashMap<>();
            change.put("submission_id", c.getRejudgeOfId());
            change.put("before", original == null ? "" : original.getStatus());
            change.put("after", c.getStatus());
            return change;
        }).toList();
        return ResponseEntity.ok(Map.of("id", batch.getId(), "state", batch.getState(), "changes", changes));
    }

    @PostMapping("/{id}/rejudges/{batchId}")
    @Transactional
    public ResponseEntity<?> applyRejudge(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer batchId, @RequestBody ReviewRequest request) {
        var user = requireCapability(id, auth, "rejudge");
        var batch = rejudges.findById(batchId).filter(x -> id.equals(x.getContestId())).orElse(null);
        if (batch == null) return ResponseEntity.notFound().build();
        if (!batch.isPending()) return ResponseEntity.status(409).body(Map.of("error", "复判批次已处理"));
        if (!java.util.Set.of("apply", "cancel").contains(request.action())) {
            return ResponseEntity.badRequest().body(Map.of("error", "action 必须为 apply 或 cancel"));
        }
        var contest = lockContest(id);
        if (contest != null && "FINALIZED".equals(contest.getLifecycleState())) {
            permission.require(id, user, "control");
        }
        if (contest != null && "CANCELLED".equals(contest.getLifecycleState()) && !"cancel".equals(request.action())) {
            return ResponseEntity.status(409).body(Map.of("error", "已取消比赛不能应用复判"));
        }
        var candidates = contestSubmissions.findByIdempotencyKeyStartingWith("rejudge:" + batchId + ":");
        if ("apply".equals(request.action())) {
            // 双人复核：敏感批次必须由另一名裁判独立审核，不能与创建者同一人。
            boolean sensitive = dualReviewMin > 0 && (candidates.size() >= dualReviewMin
                    || (contest != null && "FINALIZED".equals(contest.getLifecycleState())));
            if (sensitive && user.getId().equals(batch.getActorId())) {
                return ResponseEntity.status(403).body(Map.of("error", "敏感复判需要另一名裁判独立审核"));
            }
            // 重新认证：本地密码 + 配置/强制时的 TOTP。
            try { reauth.reauthenticate(user, request.password(), request.totp()); }
            catch (com.xauat.oj.common.exception.OjException exception) { return ResponseEntity.status(403).body(Map.of("error", exception.getMessage())); }
            if (candidates.stream().anyMatch(c -> !VALID_REJUDGE_STATUSES.contains(c.getStatus()))) {
                return ResponseEntity.status(409).body(Map.of("error", "候选结果未完成或包含系统错误"));
            }
            for (var candidate : candidates) {
                var original = candidate.getRejudgeOfId() == null ? null : contestSubmissions.findById(candidate.getRejudgeOfId()).orElse(null);
                if (original == null) continue;
                if (candidate.getRejudgeBaseAttempt() != null && original.getAttemptId() != candidate.getRejudgeBaseAttempt()) {
                    return ResponseEntity.status(409).body(Map.of("error", "原始判定已经变化，请重新创建复判批次"));
                }
                if (original.getVerdict() != null) {
                    judgements.save(Judgement.of(original, original.getAttemptId(), original.getVerdict(), original.getTestcaseResults(), null, null));
                }
                original.applyCandidate(candidate);
                contestSubmissions.save(original);
            }
            batch.apply(user.getId());
            if (contest != null) {
                contest.requestScoreboardRefresh();
                if ("FINALIZED".equals(contest.getLifecycleState())) {
                    contest.bumpFinalRevision();
                    contests.save(contest);
                    if (scoreboard.isFrozen(contest)) scoreboard.ensure(contest, ContestScoreboardService.PUBLIC_FREEZE);
                    scoreboard.ensure(contest, scoreboard.kindFor(contest, false));
                } else {
                    contests.save(contest);
                }
            }
        } else {
            batch.cancel(user.getId());
        }
        rejudges.save(batch);
        audits.save(ContestAudit.of(contest, user, "REJUDGE_" + batch.getState(), request.reason(), "{\"batch_id\":" + batchId + "}"));
        return ResponseEntity.ok(Map.of("id", batch.getId(), "state", batch.getState()));
    }

    @PostMapping("/{id}/teams") @Transactional
    public ResponseEntity<?> createTeam(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody TeamRequest request) {
        var actor = requireCapability(id, auth, "control");
        var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        if (java.util.List.of("CANCELLED", "FINALIZED").contains(contest.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛不接受队伍变更"));
        if (contest.getStartTime() != null && !java.time.LocalDateTime.now().isBefore(contest.getStartTime())) return ResponseEntity.status(409).body(Map.of("error", "开赛后不能变更队伍"));
        var memberIds = request.memberIds();
        if (memberIds == null || memberIds.isEmpty() || memberIds.size() > 3 || new java.util.HashSet<>(memberIds).size() != memberIds.size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "队伍需要 1 至 3 名不同成员"));
        }
        var memberUsers = users.findAllById(memberIds);
        if (memberUsers.size() != memberIds.size()) return ResponseEntity.badRequest().body(Map.of("error", "成员不存在"));
        var team = teams.save(ContestTeam.create(contest, memberUsers.get(0), request.name()));
        for (var member : memberUsers) {
            members.save(ContestTeamMember.create(contest, team, member));
            if (participants.findByContest_IdAndUser_Id(id, member.getId()).isEmpty()) participants.save(ContestParticipant.join(contest, member));
        }
        audits.save(ContestAudit.of(contest, actor, "TEAM_CREATE", "组建比赛队伍", "{\"team_id\":" + team.getId() + ",\"member_ids\":" + memberIds + "}"));
        contest.requestScoreboardRefresh(); contests.save(contest);
        return ResponseEntity.status(201).body(Map.of("id", team.getId(), "name", team.getName()));
    }

    @GetMapping("/{id}/teams") public ResponseEntity<?> teamList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(teams.findByContest_IdOrderByIdAsc(id).stream().map(t -> Map.of("id", t.getId(), "name", t.getName())).toList()); }

    @PostMapping("/{id}/roles") @Transactional
    public ResponseEntity<?> assignRole(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody RoleRequest request) { manager(auth); var contest = lockContest(id); var user = users.findById(request.userId()).orElse(null); if (contest == null || user == null) return ResponseEntity.notFound().build(); var role = roles.save(ContestRole.create(contest, user, request.role())); return ResponseEntity.status(201).body(Map.of("user_id", role.getUserId(), "role", role.getRole())); }

    @GetMapping("/{id}/roles") public ResponseEntity<?> roleList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(roles.findByContest_IdOrderByIdAsc(id).stream().map(r -> Map.of("user_id", r.getUserId(), "role", r.getRole())).toList()); }

    @PostMapping("/{id}/submissions/{submissionId}/override") @Transactional
    public ResponseEntity<?> override(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer submissionId, @Valid @RequestBody OverrideRequest request) {
        var user = requireCapability(id, auth, "control");
        var contest = lockContest(id);
        if (contest == null) return ResponseEntity.notFound().build();
        if (java.util.List.of("DRAFT", "READY", "CANCELLED", "FINALIZED").contains(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "当前比赛状态禁止改判"));
        }
        if (!java.util.Set.of("AC", "WA", "CE", "TLE", "MLE", "OLE", "RE").contains(request.verdict())) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的人工判定"));
        }
        try { reauth.reauthenticate(user, request.password(), request.totp()); }
        catch (com.xauat.oj.common.exception.OjException exception) { return ResponseEntity.status(403).body(Map.of("error", exception.getMessage())); }
        var item = contestSubmissions.findById(submissionId).filter(s -> id.equals(s.getContestId()) && s.isContestEligible()).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        if (!VALID_REJUDGE_STATUSES.contains(item.getStatus())) return ResponseEntity.status(409).body(Map.of("error", "处理中提交需要等待结束后改判"));
        if (item.getVerdict() != null) judgements.save(Judgement.of(item, item.getAttemptId(), item.getVerdict(), item.getTestcaseResults(), null, null));
        var problem = contestProblems.findById(item.getContestProblemId()).orElse(null);
        boolean oi = contest.getContestType() != null && contest.getContestType().toLowerCase(java.util.Locale.ROOT).contains("oi");
        int score = oi && "AC".equals(request.verdict()) && problem != null ? problem.getScore() : 0;
        item.overrideVerdict(request.verdict(), score);
        contestSubmissions.save(item);
        events.save(ContestEvent.of(contest, "judgement", "public", "{\"submission_id\":" + submissionId + ",\"status\":" + json(request.verdict()) + "}"));
        contest.requestScoreboardRefresh();
        contests.save(contest);
        audits.save(ContestAudit.of(contest, user, "OVERRIDE", request.reason(), "{\"submission_id\":" + submissionId + "}"));
        return ResponseEntity.ok(Map.of("submission_id", submissionId, "verdict", request.verdict()));
    }

    @PostMapping("/{id}/problems/{problemId}/packages") @Transactional
    public ResponseEntity<?> stagePackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer problemId, @RequestBody StageRequest request) {
        var user = requireCapability(id, auth, "package");
        var problem = contestProblems.findByIdAndContest_Id(problemId, id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();
        try {
            var item = packageService.stage(problem, request.packageData(), user.getId());
            audits.save(ContestAudit.of(lockContest(id), user, "PACKAGE_STAGE", request.reason(), "{\"digest\":\"" + item.getDigest() + "\"}"));
            return ResponseEntity.status(202).body(Map.of("digest", item.getDigest(), "state", item.getValidationState()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/{id}/packages/{digest}")
    public ResponseEntity<?> getPackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable String digest) {
        requireCapability(id, auth, "package");
        return packages.findById(digest).filter(p -> contestProblems.findByIdAndContest_Id(p.getProblemId(), id).isPresent())
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of("digest", p.getDigest(), "state", p.getValidationState(), "error", p.getValidationError() == null ? "" : p.getValidationError())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/packages/{digest}") @Transactional
    public ResponseEntity<?> activatePackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable String digest, @RequestBody(required=false) ReasonRequest request) {
        var user = requireCapability(id, auth, "control");
        var item = packages.findById(digest).filter(p -> contestProblems.findByIdAndContest_Id(p.getProblemId(), id).isPresent()).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        try {
            packageService.activate(digest, user.getId());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        audits.save(ContestAudit.of(lockContest(id), user, "PACKAGE_ACTIVATE", request == null ? null : request.reason(), "{\"digest\":\"" + digest + "\"}"));
        return ResponseEntity.ok(Map.of("digest", digest, "active", true));
    }

    @PostMapping("/{id}/rules") @Transactional
    public ResponseEntity<?> updateRules(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @RequestBody RulesRequest request) {
        var user = manager(auth); var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        contest.updateRules(request.rulesVersion(), request.allowedLanguages(), request.penaltyTime(), request.activeSubmissionLimit(), request.freezeTime()); contests.save(contest);
        audits.save(ContestAudit.of(contest, user, "RULES_UPDATE", "contest rules updated", "{}"));
        return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime()));
    }

    @GetMapping("/{id}/rules")
    public ResponseEntity<?> rules(@PathVariable Integer id) { var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "contest_type", contest.getContestType(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime())); }

    private Contest contest(Integer id) { return contests.findById(id).orElse(null); }
    private Contest lockContest(Integer id) { return contests.findForUpdateById(id).orElse(null); }
    private com.xauat.oj.core.user.domain.User manager(String auth) { var user = currentUser.require(auth); if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); return user; }
    private com.xauat.oj.core.user.domain.User actor(String auth) { return currentUser.require(auth); }
    private com.xauat.oj.core.user.domain.User requireCapability(Integer contestId, String auth, String capability) { var user = actor(auth); permission.require(contestId, user, capability); return user; }
    private Map<String,Object> clarificationView(ContestClarification item) { Map<String,Object> value = new HashMap<>(); value.put("id", item.getId()); value.put("question", item.getQuestion()); value.put("answer", item.getAnswer() == null ? "" : item.getAnswer()); return value; }
    private static String json(String value) { return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")) + "\""; }
    private static final java.util.Set<String> VALID_REJUDGE_STATUSES =
            java.util.Set.of("AC", "WA", "CE", "TLE", "MLE", "OLE", "RE", "SIGSEGV", "SIGSYS", "Partial");

    public record ClarificationRequest(@NotBlank @jakarta.validation.constraints.Size(max = 4096) String question) {}
    public record AnswerRequest(@NotBlank @jakarta.validation.constraints.Size(max = 8192) String answer, boolean broadcast) {}
    public record RejudgeRequest(@com.fasterxml.jackson.annotation.JsonProperty("submission_ids") @com.fasterxml.jackson.annotation.JsonAlias("submissionIds") java.util.List<Integer> submissionIds, String reason) {}
    public record ReviewRequest(String action, String reason, String password, String totp) {}
    public record TeamRequest(@NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                              @com.fasterxml.jackson.annotation.JsonProperty("member_ids")
                              @com.fasterxml.jackson.annotation.JsonAlias("memberIds") java.util.List<Integer> memberIds) {}
    public record RoleRequest(@com.fasterxml.jackson.annotation.JsonProperty("user_id") @com.fasterxml.jackson.annotation.JsonAlias("userId") Integer userId, @NotBlank String role) {}
    public record OverrideRequest(@NotBlank String verdict, @NotBlank String reason, String password, String totp) {}
    public record StageRequest(@com.fasterxml.jackson.annotation.JsonProperty("package") Map<String, Object> packageData, String reason) {}
    public record ReasonRequest(String reason) {}
    public record RulesRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("rules_version") @com.fasterxml.jackson.annotation.JsonAlias("rulesVersion") String rulesVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("allowed_languages") @com.fasterxml.jackson.annotation.JsonAlias("allowedLanguages") String allowedLanguages,
            @com.fasterxml.jackson.annotation.JsonProperty("penalty_time") @com.fasterxml.jackson.annotation.JsonAlias("penaltyTime") Integer penaltyTime,
            @com.fasterxml.jackson.annotation.JsonProperty("active_submission_limit") @com.fasterxml.jackson.annotation.JsonAlias("activeSubmissionLimit") Integer activeSubmissionLimit,
            @com.fasterxml.jackson.annotation.JsonProperty("freeze_time") @com.fasterxml.jackson.annotation.JsonAlias("freezeTime") java.time.LocalDateTime freezeTime) {}
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
