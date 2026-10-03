package com.xauat.oj.api.contest;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestEvent;
import com.xauat.oj.core.contest.domain.ContestParticipant;
import com.xauat.oj.core.contest.domain.ContestScoreboardSnapshot;
import com.xauat.oj.core.contest.repository.ContestEventRepository;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.core.contest.repository.ContestParticipantRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.JudgementRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/contests")
public class ContestController {
    private static final List<String> HIDDEN_STATES = List.of("DRAFT", "READY");

    private final CurrentUser currentUser; private final ContestRepository contests; private final ContestParticipantRepository participants; private final ContestProblemRepository problems;
    private final ContestSubmissionRepository submissions; private final ContestJudgeOutboxRepository outboxes;
    private final ObjectMapper mapper;
    private final ContestTestcaseRepository testcases;
    private final ContestScoreboardService scoreboard;
    private final JudgementRepository judgements;
    private final ContestEventRepository events;
    private final PackageService packageService;
    private final EntryResolver entryResolver;
    private final com.xauat.oj.api.submission.SubmissionCache submissionCache;

    public ContestController(CurrentUser currentUser, ContestRepository contests, ContestParticipantRepository participants, ContestProblemRepository problems,
                             ContestSubmissionRepository submissions, ContestJudgeOutboxRepository outboxes, ObjectMapper mapper,
                             ContestTestcaseRepository testcases, ContestScoreboardService scoreboard,
                             JudgementRepository judgements, ContestEventRepository events, PackageService packageService,
                             EntryResolver entryResolver, com.xauat.oj.api.submission.SubmissionCache submissionCache) {
        this.currentUser = currentUser; this.contests = contests; this.participants = participants; this.problems = problems;
        this.submissions = submissions; this.outboxes = outboxes; this.mapper = mapper; this.testcases = testcases;
        this.scoreboard = scoreboard; this.judgements = judgements; this.events = events; this.packageService = packageService;
        this.entryResolver = entryResolver; this.submissionCache = submissionCache;
    }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return contests.findAll().stream()
                .filter(c -> c.isPublic() && !"DRAFT".equals(c.getLifecycleState()) && !"CANCELLED".equals(c.getLifecycleState()))
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(statusOf(c)))
                .sorted(java.util.Comparator.comparing(Contest::getStartTime, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())).thenComparing(Contest::getId, java.util.Comparator.reverseOrder()))
                .map(this::view).toList();
    }

    @PostMapping({"", "/"})
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody ContestRequest request) {
        var user = requireManager(authorization);
        String type = normalizeType(request.contestType());
        if (type == null) return ResponseEntity.badRequest().body(Map.of("error", "比赛模式仅支持 ACM 或 OI"));
        String invalid = validateTimes(request.startTime(), request.endTime(), request.freezeTime());
        if (invalid != null) return ResponseEntity.badRequest().body(Map.of("error", invalid));
        Contest contest = Contest.createFull(request.title().trim(), request.description().trim(), type, request.startTime(), request.endTime(),
                user.getId(), request.penaltyTime() == null ? 20 : request.penaltyTime(), request.freezeTime(), inferStatus(request.startTime(), request.endTime()));
        return ResponseEntity.status(201).body(view(contests.save(contest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var contest = contests.findById(id).orElse(null);
        if (contest == null) return ResponseEntity.notFound().build();
        if ((!contest.isPublic() || "DRAFT".equals(contest.getLifecycleState())) && !isManager(authorization)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(view(contest));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id,
                                    @RequestBody ContestRequest request) {
        requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        if (!HIDDEN_STATES.contains(item.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛发布后不能直接修改规则或时间"));
        String type = request.contestType() == null ? null : normalizeType(request.contestType());
        if (request.contestType() != null && type == null) return ResponseEntity.badRequest().body(Map.of("error", "比赛模式仅支持 ACM 或 OI"));
        LocalDateTime start = request.startTime() != null ? request.startTime() : item.getStartTime();
        LocalDateTime end = request.endTime() != null ? request.endTime() : item.getEndTime();
        LocalDateTime freeze = request.freezeTime() != null ? request.freezeTime() : item.getFreezeTime();
        String invalid = validateTimes(start, end, freeze);
        if (invalid != null) return ResponseEntity.badRequest().body(Map.of("error", invalid));
        item.updateConfig(request.title(), request.description(), type, request.startTime(), request.endTime(), request.freezeTime(),
                request.freezeTime() != null || item.getFreezeTime() == null, request.penaltyTime(), inferStatus(start, end));
        return ResponseEntity.ok(view(contests.save(item)));
    }

    @GetMapping("/manage")
    public ResponseEntity<?> manage(@RequestHeader(value = "Authorization", required = false) String authorization) {
        requireManager(authorization);
        return ResponseEntity.ok(contests.findAll().stream()
                .sorted(java.util.Comparator.comparing(Contest::getStartTime, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::view).toList());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        requireManager(authorization);
        var contest = contests.findForUpdateById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build();
        if (!"DRAFT".equals(contest.getLifecycleState())) {
            return ResponseEntity.status(409).body(Map.of("error", "已发布比赛必须保留审计记录；请取消比赛，不能删除"));
        }
        try {
            judgements.deleteByContestId(id);
            outboxes.deleteByContestId(id);
            submissions.deleteByContestId(id);
            testcases.deleteByContestId(id);
            problems.deleteByContestId(id);
            participants.deleteByContestId(id);
            contests.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(Map.of("error", "服务暂时不可用"));
        }
    }

    @PostMapping("/{id}/publish")
    @Transactional
    public ResponseEntity<?> publish(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        if (!HIDDEN_STATES.contains(item.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛已发布，不能再次发布"));
        if (item.getStartTime() == null || item.getEndTime() == null) return ResponseEntity.badRequest().body(Map.of("error", "发布比赛必须设置开始和结束时间"));
        var contestProblems = problems.findByContest_IdOrderBySortOrderAscIdAsc(id);
        if (contestProblems.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "发布比赛至少需要一道题目"));
        for (var problem : contestProblems) {
            if (!"VALID".equalsIgnoreCase(problem.getValidationStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "题目 " + problem.getProblemIndex() + " 的参考答案尚未验证通过"));
            }
            boolean hasHidden = testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId()).stream().anyMatch(t -> !t.isSample());
            if (!hasHidden) return ResponseEntity.badRequest().body(Map.of("error", "题目 " + problem.getProblemIndex() + " 缺少隐藏测试数据"));
        }
        for (var problem : contestProblems) {
            problem.attachPackage(packageService.publish(problem, user.getId()).getDigest());
            problems.save(problem);
        }
        item.publish();
        item.requestScoreboardRefresh();
        return ResponseEntity.ok(view(contests.save(item)));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancel(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        if ("FINALIZED".equals(item.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "最终榜已结算，不能取消"));
        item.cancel();
        return ResponseEntity.ok(view(contests.save(item)));
    }

    @PostMapping("/{id}/join")
    @Transactional
    public ResponseEntity<?> join(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization);
        var contest = contests.findForUpdateById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build();
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isPresent()) {
            return ResponseEntity.ok(Map.of("success", true, "already_joined", true));
        }
        if (!List.of("SCHEDULED", "RUNNING").contains(contest.getLifecycleState()) || !contest.isPublic()) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛未开放报名"));
        }
        if (contest.getStartTime() == null || !LocalDateTime.now().isBefore(contest.getStartTime())) {
            return ResponseEntity.status(409).body(Map.of("error", "比赛开始后名单已锁定"));
        }
        participants.save(ContestParticipant.join(contest, user));
        contest.requestScoreboardRefresh();
        contests.save(contest);
        events.save(ContestEvent.of(contest, "entry", "jury", "{\"entry_id\":" + user.getId() + "}"));
        return ResponseEntity.status(201).body(Map.of("success", true, "message", "报名成功"));
    }

    @GetMapping("/{id}/problems")
    public ResponseEntity<?> problemList(@PathVariable Integer id) { if (!contests.existsById(id)) return ResponseEntity.notFound().build(); return ResponseEntity.ok(problems.findByContest_IdOrderBySortOrderAscIdAsc(id).stream().map(this::publicProblem).toList()); }

    @GetMapping("/{id}/problems/{problemId}")
    public ResponseEntity<?> problem(@PathVariable Integer id, @PathVariable Integer problemId) {
        return problems.findByIdAndContest_Id(problemId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(publicProblem(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/problems/{problemId}")
    public ResponseEntity<?> problemGlobal(@PathVariable Integer problemId) { return problems.findById(problemId).<ResponseEntity<?>>map(p -> ResponseEntity.ok(publicProblem(p))).orElseGet(() -> ResponseEntity.notFound().build()); }

    /** 参赛者题面：不返回参考答案、校验信息与题包摘要。 */
    private Map<String, Object> publicProblem(com.xauat.oj.core.contest.domain.ContestProblem problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", problem.getId());
        body.put("contest_id", problem.getContestId());
        body.put("problem_index", problem.getProblemIndex());
        body.put("title", problem.getTitle());
        body.put("description", problem.getDescription());
        body.put("input_desc", problem.getInputDesc());
        body.put("output_desc", problem.getOutputDesc());
        body.put("difficulty", problem.getDifficulty());
        body.put("time_limit", problem.getTimeLimit());
        body.put("memory_limit", problem.getMemoryLimit());
        body.put("testcase_count", testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId()).size());
        body.put("samples", parseSamples(problem.getSamples()));
        return body;
    }

    private List<Map<String, Object>> parseSamples(String raw) {
        try {
            if (raw == null || raw.isBlank()) return List.of();
            return mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    @PostMapping("/{id}/problems/{problemId}/submit")
    @Transactional
    public ResponseEntity<?> submit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @PathVariable Integer id, @PathVariable Integer problemId, @Valid @RequestBody SubmitRequest request) {
        var user = currentUser.require(authorization);
        var contest = contests.findForUpdateById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build();
        if (!List.of("SCHEDULED", "RUNNING", "FROZEN").contains(contest.getLifecycleState())) return ResponseEntity.status(409).body(Map.of("error", "比赛当前不可提交"));
        if (contest.getStartTime() != null && LocalDateTime.now().isBefore(contest.getStartTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛尚未开始"));
        if (contest.getEndTime() != null && !LocalDateTime.now().isBefore(contest.getEndTime())) return ResponseEntity.status(409).body(Map.of("error", "比赛已经结束"));
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isEmpty()) return ResponseEntity.status(403).body(Map.of("error", "请先报名比赛"));
        var problem = problems.findByIdAndContest_Id(problemId, id).orElse(null); if (problem == null) return ResponseEntity.notFound().build();
        if (idempotencyKey != null) {
            var replay = submissions.findByContest_IdAndUser_IdAndIdempotencyKey(id, user.getId(), idempotencyKey);
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
        String key = "cache:contest_submission:" + submissionId;
        var cached = submissionCache.get(key);
        if (cached.isPresent()) return ResponseEntity.ok(cached.get());
        return submissions.findById(submissionId).filter(s -> id.equals(s.getContestId()) && user.getId().equals(s.getUserId()) && s.getRejudgeOfId() == null)
                .<ResponseEntity<?>>map(s -> {
                    Map<String, Object> body = submissionView(s, false);
                    if (!List.of("Pending", "Queued", "Judging", "Claimed", "Compiling", "Compiled", "Running", "Checking").contains(s.getStatus())) submissionCache.put(key, body);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/statuses")
    public ResponseEntity<?> statuses(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        if (!contests.existsById(id)) return ResponseEntity.notFound().build();
        var user = currentUser.require(authorization);
        Integer myEntry = entryResolver.entryUser(id, user.getId());
        var items = submissions.findByContest_IdOrderByIdDesc(id).stream()
                .filter(com.xauat.oj.core.contest.domain.ContestSubmission::isContestEligible)
                .filter(s -> s.getRejudgeOfId() == null)
                .filter(s -> myEntry.equals(entryResolver.entryUser(id, s.getUserId())))
                .toList();
        java.util.LinkedHashMap<String, String> latest = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Boolean> anyAccepted = new java.util.LinkedHashMap<>();
        for (var s : items) {
            latest.putIfAbsent(s.getProblemIndex(), s.getStatus() == null ? "" : s.getStatus());
            if ("AC".equalsIgnoreCase(s.getVerdict()) || "Accepted".equalsIgnoreCase(s.getVerdict()) || "AC".equalsIgnoreCase(s.getStatus())) anyAccepted.put(s.getProblemIndex(), true);
        }
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        latest.forEach((index, status) -> body.put(index, Map.of("status", status, "solved", anyAccepted.getOrDefault(index, false))));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/rankings")
    public ResponseEntity<?> rankings(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable Integer id, @RequestHeader(value="If-None-Match", required=false) String ifNoneMatch) {
        var contest = contests.findById(id).orElse(null); if (contest == null) return ResponseEntity.notFound().build();
        boolean jury = false; var user = currentUser.optional(authorization);
        if (user != null) jury = "manager".equals(user.getRole()) || "staff".equals(user.getRole());
        boolean participant = user != null && participants.findByContest_IdAndUser_Id(id, user.getId()).isPresent();
        boolean visible = jury || participant || (contest.isPublic() && !List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState()));
        if (!visible) return ResponseEntity.notFound().build();
        ContestScoreboardSnapshot snapshot = scoreboard.ensure(contest, scoreboard.kindFor(contest, jury));
        return snapshotResponse(snapshot, ifNoneMatch, scoreboard.bodyOf(snapshot));
    }

    private ResponseEntity<?> snapshotResponse(ContestScoreboardSnapshot snapshot, String ifNoneMatch, Object body) { String etag = "\"" + sha256(snapshot.getPayload()) + "\""; if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).header(HttpHeaders.ETAG, etag).build(); return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).header("X-Scoreboard-Version", String.valueOf(snapshot.getScoreboardVersion())).body(body); }

    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }

    private String statusOf(Contest contest) {
        String lifecycle = contest.getLifecycleState();
        if ("FINALIZED".equals(lifecycle) || "CANCELLED".equals(lifecycle)) return "past";
        if ("DRAFT".equals(lifecycle) || "READY".equals(lifecycle)) return "upcoming";
        LocalDateTime now = LocalDateTime.now();
        if (contest.getStartTime() != null && now.isBefore(contest.getStartTime())) return "upcoming";
        if (contest.getEndTime() != null && !now.isBefore(contest.getEndTime())) return "past";
        return "ongoing";
    }

    private boolean isFrozen(Contest contest) {
        if (contest.getFreezeTime() == null || contest.getThawedAt() != null) return false;
        if (List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState())) return false;
        return !LocalDateTime.now().isBefore(contest.getFreezeTime());
    }

    private String inferStatus(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "upcoming";
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start)) return "upcoming";
        if (now.isAfter(end)) return "past";
        return "ongoing";
    }

    private String validateTimes(LocalDateTime start, LocalDateTime end, LocalDateTime freeze) {
        if (start != null && end != null && !end.isAfter(start)) return "结束时间必须晚于开始时间";
        if (freeze != null && (start == null || end == null || !freeze.isAfter(start) || !freeze.isBefore(end))) {
            return "封榜时间必须位于开始与结束时间之间";
        }
        return null;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return "ACM";
        String upper = type.toUpperCase(java.util.Locale.ROOT);
        return List.of("ACM", "OI").contains(upper) ? upper : null;
    }

    private boolean isManager(String authorization) {
        var user = currentUser.optional(authorization);
        return user != null && "manager".equals(user.getRole());
    }

    private com.xauat.oj.core.user.domain.User requireManager(String authorization) {
        var user = currentUser.require(authorization);
        if (!"manager".equals(user.getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足");
        return user;
    }

    private Map<String, Object> view(Contest item) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", item.getId());
        body.put("title", item.getTitle());
        body.put("description", item.getDescription());
        body.put("contest_type", item.getContestType());
        body.put("status", statusOf(item));
        body.put("start_time", item.getStartTime());
        body.put("end_time", item.getEndTime());
        body.put("freeze_time", item.getFreezeTime());
        body.put("penalty_time", item.getPenaltyTime());
        body.put("lifecycle_state", item.getLifecycleState());
        body.put("is_frozen", isFrozen(item));
        body.put("allowed_languages", item.getAllowedLanguages());
        body.put("participants_count", participants.countByContest_Id(item.getId()));
        body.put("created_at", item.getCreatedAt());
        return body;
    }

    private Map<String, Object> submissionView(com.xauat.oj.core.contest.domain.ContestSubmission item, boolean replay) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", item.getId());
        body.put("contest_id", item.getContestId());
        body.put("problem_index", item.getProblemIndex());
        body.put("status", item.getStatus());
        body.put("verdict", item.getVerdict() == null ? "" : item.getVerdict());
        body.put("job_id", item.getJobId());
        body.put("idempotent_replay", replay);
        return body;
    }

    public record ContestRequest(@NotBlank @jakarta.validation.constraints.Size(max = 200) String title,
                                 @NotBlank @jakarta.validation.constraints.Size(max = 131072) String description,
                                 @JsonProperty("contest_type") @JsonAlias("contestType") String contestType,
                                 @JsonProperty("start_time") @JsonAlias("startTime") LocalDateTime startTime,
                                 @JsonProperty("end_time") @JsonAlias("endTime") LocalDateTime endTime,
                                 @JsonProperty("freeze_time") @JsonAlias("freezeTime") LocalDateTime freezeTime,
                                 @JsonProperty("penalty_time") @JsonAlias("penaltyTime") Integer penaltyTime) {}
    public record SubmitRequest(@NotBlank @jakarta.validation.constraints.Size(max = 131072) String code,
                                @NotBlank @jakarta.validation.constraints.Size(max = 50) String language) {}
}
