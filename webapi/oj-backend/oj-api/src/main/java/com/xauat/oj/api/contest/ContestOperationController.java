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

/**
 * 比赛运营（裁判侧）接口：封榜/解封、结算、健康度、事件流、答疑、复判、队伍、角色、
 * 人工改判、题包 staging/激活与规则维护。写操作需要比赛内 capability（control/jury/rejudge/package）
 * 或全局 manager，敏感操作还需 {@link JuryReauthService} 重新认证。
 */
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

    /**
     * 手动解封：仅 manager/staff，解除封榜并记录审计。
     *
     * @param auth 认证头
     * @param id   比赛 ID
     * @return 比赛生命周期状态，或 404
     */
    @PostMapping("/{id}/thaw") @Transactional
    public ResponseEntity<?> thaw(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) {
        var user = manager(auth); var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build(); contest.thaw(); contests.save(contest); audits.save(ContestAudit.of(contest, user, "THAW", "manual thaw", "{}")); return ResponseEntity.ok(Map.of("id", id, "lifecycle_state", contest.getLifecycleState()));
    }

    /**
     * 结算比赛：需要 control 能力。要求比赛已发布、已结束，且无进行中判题与待审核复判批次；
     * 封榜时先固化 PUBLIC_FREEZE 快照，再结算并生成最终快照。重复调用幂等返回现有结果。
     *
     * @param auth 认证头
     * @param id   比赛 ID
     * @return 最终榜单，或 404/409
     */
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
        // 结算前置条件：不存在未完成判题的提交
        long unresolved = contestSubmissions.findByContest_IdOrderByIdDesc(id).stream()
                .filter(com.xauat.oj.core.contest.domain.ContestSubmission::isContestEligible)
                .filter(s -> !VALID_REJUDGE_STATUSES.contains(s.getStatus())).count();
        if (unresolved > 0) return ResponseEntity.status(409).body(Map.of("error", "仍有判题任务未完成，不能结算", "count", unresolved));
        // 且不存在待审核的复判批次
        if (rejudges.countByContest_IdAndState(id, "PENDING") > 0) return ResponseEntity.status(409).body(Map.of("error", "存在待审核重判批次"));
        // 封榜状态下先固化冻结快照，保留结算前的可见成绩
        if (scoreboard.isFrozen(contest)) scoreboard.ensure(contest, ContestScoreboardService.PUBLIC_FREEZE);
        contest.finalizeContest();
        contests.save(contest);
        var snapshot = scoreboard.ensure(contest, scoreboard.kindFor(contest, false));
        audits.save(ContestAudit.of(contest, user, "FINALIZE", "审核并确认最终成绩", "{}"));
        return ResponseEntity.ok(scoreboard.bodyOf(snapshot));
    }

    /**
     * 比赛健康度：统计待判/通过提交、等待队列、投影版本滞后与系统错误等运维指标。
     *
     * @param auth 认证头
     * @param id   比赛 ID
     * @return 健康度指标，或 404
     */
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
        // 投影滞后：请求版本与已生成 LIVE 快照版本之差，反映榜单新鲜度
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

    /**
     * 比赛事件流：按游标分页返回，依据事件受众（public/jury/entry）过滤，选手只能看到公开事件与自己相关的事件。
     *
     * @param auth  认证头
     * @param id    比赛 ID
     * @param after 游标，大于 0 时增量拉取
     * @return 事件列表与下一个游标，或 404
     */
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
        // 受众过滤：公开事件人人可见；裁判可见全部；entry 事件仅本人或匿名广播的参赛者可见
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

    /**
     * 比赛审计日志：仅 manager/staff 可见，按时间倒序返回操作记录。
     *
     * @param auth 认证头
     * @param id   比赛 ID
     * @return 审计记录列表，或 404
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<?> audit(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(audits.findByContest_IdOrderByIdDesc(id).stream().map(a -> Map.of("id", a.getId(), "action", a.getAction(), "reason", a.getReason(), "payload", a.getPayload())).toList()); }

    /**
     * 答疑列表：按时间倒序返回该比赛的提问与答复。
     *
     * @param id 比赛 ID
     * @return 答疑列表，或 404
     */
    @GetMapping("/{id}/clarifications")
    public ResponseEntity<?> clarificationList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(clarifications.findByContest_IdOrderByIdDesc(id).stream().map(this::clarificationView).toList()); }

    /**
     * 发起提问：参赛者或裁判；比赛处于进行态才接受提问。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param request 提问内容
     * @return 201 与答疑视图，或 403/404/409
     */
    @PostMapping("/{id}/clarifications") @Transactional
    public ResponseEntity<?> ask(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody ClarificationRequest request) {
        var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        var user = currentUser.require(auth);
        // 仅参赛者或具备 jury 能力者可提问
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isEmpty() && !permission.allowed(id, user, "jury")) {
            return ResponseEntity.status(403).body(Map.of("error", "需要比赛参赛身份"));
        }
        if (java.util.List.of("DRAFT", "READY", "CANCELLED", "FINALIZED").contains(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛暂不接受提问"));
        }
        var item = clarifications.save(ContestClarification.ask(contest, user, request.question()));
        return ResponseEntity.status(201).body(clarificationView(item));
    }

    /**
     * 回复答疑：需要 jury 能力。若问题已被其他裁判认领则拒绝，避免重复答复；
     * 广播答复写入 public 事件，否则仅向提问者推送 entry 事件。
     *
     * @param auth       认证头
     * @param id         比赛 ID
     * @param questionId 问题 ID
     * @param request    答复内容与是否广播
     * @return 答疑视图，或 404/409
     */
    @PostMapping("/{id}/clarifications/{questionId}") @Transactional
    public ResponseEntity<?> answer(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable Integer questionId, @Valid @RequestBody AnswerRequest request) {
        var user = requireCapability(id, auth, "jury");
        var item = clarifications.findById(questionId).filter(q -> id.equals(q.getContestId())).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        // 认领冲突保护：已被他人认领的问题不能重复答复
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

    /**
     * 创建复判批次：需要 rejudge 能力。校验提交归属与终态后，
     * 为每条原提交克隆一份复判提交并写入判题 outbox，批次进入待审核状态。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param request 原提交 ID 列表与原因
     * @return 202 与批次信息，或 400/404/409
     */
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
        // 为每条原提交克隆复判提交：幂等键前缀关联批次，便于后续 apply 时定位候选结果
        for (var source : originals) {
            var clone = ContestSubmission.rejudgeOf(source, UUID.randomUUID().toString(), "rejudge:" + batch.getId() + ":" + source.getId());
            contestSubmissions.save(clone);
            judgeOutboxes.save(ContestJudgeOutbox.of(clone));
        }
        audits.save(ContestAudit.of(contest, user, "REJUDGE_CREATE", request.reason(), "{\"batch_id\":" + batch.getId() + "}"));
        return ResponseEntity.status(202).body(Map.of("batch_id", batch.getId(), "state", batch.getState()));
    }

    /**
     * 复判批次列表：仅 manager/staff 可见。
     *
     * @param auth 认证头
     * @param id   比赛 ID
     * @return 批次列表，或 404
     */
    @GetMapping("/{id}/rejudges") public ResponseEntity<?> rejudgeList(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id) { manager(auth); if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(rejudges.findByContest_IdOrderByIdDesc(id).stream().map(x -> Map.of("id", x.getId(), "state", x.getState())).toList()); }

    /**
     * 复判批次详情：仅 manager/staff。列出候选结果相对原提交的状态变化，供审核。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param batchId 批次 ID
     * @return 批次与变更明细，或 404
     */
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

    /**
     * 审核复判批次：需要 rejudge 能力。apply 会将候选结果覆盖回原提交（保存旧判定到历史），
     * 并触发榜单刷新；cancel 则丢弃批次。敏感批次强制双人复核与重新认证。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param batchId 批次 ID
     * @param request 动作（apply/cancel）、原因与重认证凭据
     * @return 批次状态，或 400/403/404/409
     */
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
            // 逐条把候选结果覆盖回原提交：先归档旧判定，再应用候选，保证可追溯
            for (var candidate : candidates) {
                var original = candidate.getRejudgeOfId() == null ? null : contestSubmissions.findById(candidate.getRejudgeOfId()).orElse(null);
                if (original == null) continue;
                // 乐观并发保护：原判定已变化说明批次基线失效
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
                // 已结算比赛的复判会产生新的最终榜修订，需要重建 FINAL 快照
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

    /**
     * 组建队伍：需要 control 能力。要求比赛未取消/结算且未开赛，成员为 1 至 3 名不同用户；
     * 未报名的成员自动报名。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param request 队名与成员 ID
     * @return 201 与队伍信息，或 400/404/409
     */
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
            // 队伍成员若未报名则自动报名，保证其提交计入 entry
            if (participants.findByContest_IdAndUser_Id(id, member.getId()).isEmpty()) participants.save(ContestParticipant.join(contest, member));
        }
        audits.save(ContestAudit.of(contest, actor, "TEAM_CREATE", "组建比赛队伍", "{\"team_id\":" + team.getId() + ",\"member_ids\":" + memberIds + "}"));
        contest.requestScoreboardRefresh(); contests.save(contest);
        return ResponseEntity.status(201).body(Map.of("id", team.getId(), "name", team.getName()));
    }

    /**
     * 队伍列表。
     *
     * @param id 比赛 ID
     * @return 队伍列表，或 404
     */
    @GetMapping("/{id}/teams") public ResponseEntity<?> teamList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(teams.findByContest_IdOrderByIdAsc(id).stream().map(t -> Map.of("id", t.getId(), "name", t.getName())).toList()); }

    /**
     * 分配比赛角色（director/jury/setter/operator）：仅 manager/staff。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param request 用户 ID 与角色
     * @return 201 与角色信息，或 404
     */
    @PostMapping("/{id}/roles") @Transactional
    public ResponseEntity<?> assignRole(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @Valid @RequestBody RoleRequest request) { manager(auth); var contest = lockContest(id); var user = users.findById(request.userId()).orElse(null); if (contest == null || user == null) return ResponseEntity.notFound().build(); var role = roles.save(ContestRole.create(contest, user, request.role())); return ResponseEntity.status(201).body(Map.of("user_id", role.getUserId(), "role", role.getRole())); }

    /**
     * 比赛角色列表。
     *
     * @param id 比赛 ID
     * @return 角色列表，或 404
     */
    @GetMapping("/{id}/roles") public ResponseEntity<?> roleList(@PathVariable Integer id) { if (contest(id) == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(roles.findByContest_IdOrderByIdAsc(id).stream().map(r -> Map.of("user_id", r.getUserId(), "role", r.getRole())).toList()); }

    /**
     * 人工改判提交结论：需要 control 能力，且必须通过重新认证。
     * 改判前归档原判定，OI 赛制下 AC 按题目分值计分，随后刷新榜单并写审计。
     *
     * @param auth         认证头
     * @param id           比赛 ID
     * @param submissionId 提交 ID
     * @param request      新判定、原因与重认证凭据
     * @return 改判结果，或 400/403/404/409
     */
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
        // OI 赛制按题目分值给分，ACM 改判不产生分数
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

    /**
     * 暂存题包：需要 package 能力。委托 {@link PackageService#stage} 校验并落库为 PENDING，异步验证。
     *
     * @param auth      认证头
     * @param id        比赛 ID
     * @param problemId 题目 ID
     * @param request   题包数据与原因
     * @return 202 与摘要/状态，或 400/404
     */
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

    /**
     * 查询题包验证状态：需要 package 能力，且题包必须属于本场比赛的某题目。
     *
     * @param auth   认证头
     * @param id     比赛 ID
     * @param digest 题包摘要
     * @return 题包状态，或 404
     */
    @GetMapping("/{id}/packages/{digest}")
    public ResponseEntity<?> getPackage(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @PathVariable String digest) {
        requireCapability(id, auth, "package");
        return packages.findById(digest).filter(p -> contestProblems.findByIdAndContest_Id(p.getProblemId(), id).isPresent())
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of("digest", p.getDigest(), "state", p.getValidationState(), "error", p.getValidationError() == null ? "" : p.getValidationError())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 激活题包：需要 control 能力。题包必须已验证为 VALID，激活后成为题目当前生效版本。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param digest  题包摘要
     * @param request 可选原因
     * @return 激活结果，或 400/404/409
     */
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

    /**
     * 更新比赛规则（规则版本、语言、罚时、同时在提交数、封榜时间）：仅 manager/staff。
     *
     * @param auth    认证头
     * @param id      比赛 ID
     * @param request 规则字段
     * @return 更新后的规则快照，或 404
     */
    @PostMapping("/{id}/rules") @Transactional
    public ResponseEntity<?> updateRules(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable Integer id, @RequestBody RulesRequest request) {
        var user = manager(auth); var contest = lockContest(id); if (contest == null) return ResponseEntity.notFound().build();
        contest.updateRules(request.rulesVersion(), request.allowedLanguages(), request.penaltyTime(), request.activeSubmissionLimit(), request.freezeTime()); contests.save(contest);
        audits.save(ContestAudit.of(contest, user, "RULES_UPDATE", "contest rules updated", "{}"));
        return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime()));
    }

    /**
     * 查询比赛规则（只读）。
     *
     * @param id 比赛 ID
     * @return 规则快照，或 404
     */
    @GetMapping("/{id}/rules")
    public ResponseEntity<?> rules(@PathVariable Integer id) { var contest = contest(id); if (contest == null) return ResponseEntity.notFound().build(); return ResponseEntity.ok(Map.of("contest_id", id, "rules_version", contest.getRulesVersion(), "contest_type", contest.getContestType(), "allowed_languages", contest.getAllowedLanguages(), "penalty_time", contest.getPenaltyTime(), "active_submission_limit", contest.getActiveSubmissionLimit(), "freeze_time", contest.getFreezeTime() == null ? "" : contest.getFreezeTime())); }

    private Contest contest(Integer id) { return contests.findById(id).orElse(null); }
    /** 以悲观锁读取比赛，供写操作串行化。 */
    private Contest lockContest(Integer id) { return contests.findForUpdateById(id).orElse(null); }
    /** 要求全局 manager 或 staff 角色。 */
    private com.xauat.oj.core.user.domain.User manager(String auth) { var user = currentUser.require(auth); if (!"manager".equals(user.getRole()) && !"staff".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); return user; }
    private com.xauat.oj.core.user.domain.User actor(String auth) { return currentUser.require(auth); }
    /** 要求当前用户对指定比赛具备某项能力，否则抛 FORBIDDEN。 */
    private com.xauat.oj.core.user.domain.User requireCapability(Integer contestId, String auth, String capability) { var user = actor(auth); permission.require(contestId, user, capability); return user; }
    private Map<String,Object> clarificationView(ContestClarification item) { Map<String,Object> value = new HashMap<>(); value.put("id", item.getId()); value.put("question", item.getQuestion()); value.put("answer", item.getAnswer() == null ? "" : item.getAnswer()); return value; }
    private static String json(String value) { return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")) + "\""; }
    private static final java.util.Set<String> VALID_REJUDGE_STATUSES =
            java.util.Set.of("AC", "WA", "CE", "TLE", "MLE", "OLE", "RE", "SIGSEGV", "SIGSYS", "Partial");

    /** 提问请求。 */
    public record ClarificationRequest(@NotBlank @jakarta.validation.constraints.Size(max = 4096) String question) {}
    /** 答疑答复请求，broadcast 决定是否公开给全体。 */
    public record AnswerRequest(@NotBlank @jakarta.validation.constraints.Size(max = 8192) String answer, boolean broadcast) {}
    /** 创建复判批次请求。 */
    public record RejudgeRequest(@com.fasterxml.jackson.annotation.JsonProperty("submission_ids") @com.fasterxml.jackson.annotation.JsonAlias("submissionIds") java.util.List<Integer> submissionIds, String reason) {}
    /** 复判审核请求，包含动作与重认证凭据。 */
    public record ReviewRequest(String action, String reason, String password, String totp) {}
    /** 组建队伍请求。 */
    public record TeamRequest(@NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                              @com.fasterxml.jackson.annotation.JsonProperty("member_ids")
                              @com.fasterxml.jackson.annotation.JsonAlias("memberIds") java.util.List<Integer> memberIds) {}
    /** 分配比赛角色请求。 */
    public record RoleRequest(@com.fasterxml.jackson.annotation.JsonProperty("user_id") @com.fasterxml.jackson.annotation.JsonAlias("userId") Integer userId, @NotBlank String role) {}
    /** 人工改判请求，包含新判定、原因与重认证凭据。 */
    public record OverrideRequest(@NotBlank String verdict, @NotBlank String reason, String password, String totp) {}
    /** 暂存题包请求。 */
    public record StageRequest(@com.fasterxml.jackson.annotation.JsonProperty("package") Map<String, Object> packageData, String reason) {}
    /** 仅含原因的通用请求。 */
    public record ReasonRequest(String reason) {}
    /** 更新比赛规则请求。 */
    public record RulesRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("rules_version") @com.fasterxml.jackson.annotation.JsonAlias("rulesVersion") String rulesVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("allowed_languages") @com.fasterxml.jackson.annotation.JsonAlias("allowedLanguages") String allowedLanguages,
            @com.fasterxml.jackson.annotation.JsonProperty("penalty_time") @com.fasterxml.jackson.annotation.JsonAlias("penaltyTime") Integer penaltyTime,
            @com.fasterxml.jackson.annotation.JsonProperty("active_submission_limit") @com.fasterxml.jackson.annotation.JsonAlias("activeSubmissionLimit") Integer activeSubmissionLimit,
            @com.fasterxml.jackson.annotation.JsonProperty("freeze_time") @com.fasterxml.jackson.annotation.JsonAlias("freezeTime") java.time.LocalDateTime freezeTime) {}
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
