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
import com.xauat.oj.core.contest.repository.ContestTeamMemberRepository;
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

/**
 * 比赛（contest）对外只读与参赛接口。
 * 覆盖比赛列表/详情、题目查看、报名、提交、状态与榜单读取；
 * 所有写操作要求 manager 权限或有效的参赛身份，榜单读取基于快照并按权限控制可见性。
 */
@RestController
@RequestMapping("/contests")
public class ContestController {
    private static final List<String> HIDDEN_STATES = List.of("DRAFT", "READY");

    private final CurrentUser currentUser;
    private final ContestRepository contests;
    private final ContestParticipantRepository participants;
    private final ContestProblemRepository problems;
    private final ContestSubmissionRepository submissions;
    private final ContestJudgeOutboxRepository outboxes;
    private final ObjectMapper mapper;
    private final ContestTestcaseRepository testcases;
    private final ContestScoreboardService scoreboard;
    private final JudgementRepository judgements;
    private final ContestEventRepository events;
    private final PackageService packageService;
    private final EntryResolver entryResolver;
    private final com.xauat.oj.api.submission.SubmissionCache submissionCache;
    private final ContestTeamMemberRepository teamMembers;

    public ContestController(CurrentUser currentUser, ContestRepository contests,
            ContestParticipantRepository participants, ContestProblemRepository problems,
            ContestSubmissionRepository submissions, ContestJudgeOutboxRepository outboxes, ObjectMapper mapper,
            ContestTestcaseRepository testcases, ContestScoreboardService scoreboard,
            JudgementRepository judgements, ContestEventRepository events, PackageService packageService,
            EntryResolver entryResolver, com.xauat.oj.api.submission.SubmissionCache submissionCache,
            ContestTeamMemberRepository teamMembers) {
        this.currentUser = currentUser;
        this.contests = contests;
        this.participants = participants;
        this.problems = problems;
        this.submissions = submissions;
        this.outboxes = outboxes;
        this.mapper = mapper;
        this.testcases = testcases;
        this.scoreboard = scoreboard;
        this.judgements = judgements;
        this.events = events;
        this.packageService = packageService;
        this.entryResolver = entryResolver;
        this.submissionCache = submissionCache;
        this.teamMembers = teamMembers;
    }

    /**
     * 公开比赛列表：仅返回已公开且非草稿/取消的比赛，可按派生状态（upcoming/ongoing/past）过滤。
     *
     * @param status 可选的状态过滤，为空表示不过滤
     * @return 比赛视图列表
     */
    @GetMapping({"", "/"})
    public List<Map<String, Object>> list(@RequestParam(required = false)
    String status) {
        return contests.findAll().stream()
                // 隐藏草稿与已取消比赛，仅暴露公开可见项
                .filter(c -> c.isPublic() && !"DRAFT".equals(c.getLifecycleState())
                        && !"CANCELLED".equals(c.getLifecycleState()))
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(statusOf(c)))
                .sorted(java.util.Comparator
                        .comparing(Contest::getStartTime,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(Contest::getId, java.util.Comparator.reverseOrder()))
                .map(this::view).toList();
    }

    /**
     * 创建比赛（草稿）：仅 manager 可调用，校验赛制、时间区间与封榜时间后落库。
     *
     * @param authorization 认证头
     * @param request       比赛创建参数
     * @return 201 与比赛视图，或 400 参数错误
     */
    @PostMapping({"", "/"})
    @Transactional
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @Valid @RequestBody
            ContestRequest request) {
        var user = requireManager(authorization);
        String type = normalizeType(request.contestType());
        if (type == null)
            return ResponseEntity.badRequest().body(Map.of("error", "比赛模式仅支持 ACM 或 OI"));
        String invalid = validateTimes(request.startTime(), request.endTime(), request.freezeTime());
        if (invalid != null)
            return ResponseEntity.badRequest().body(Map.of("error", invalid));
        Contest contest = Contest.createFull(request.title().trim(), request.description().trim(), type,
                request.startTime(), request.endTime(),
                user.getId(), request.penaltyTime() == null ? 20 : request.penaltyTime(), request.freezeTime(),
                inferStatus(request.startTime(), request.endTime()));
        return ResponseEntity.status(201).body(view(contests.save(contest)));
    }

    /**
     * 比赛详情：非公开或草稿状态的比赛仅 manager 可见，其余情况对外隐藏（返回 404）。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 比赛视图或 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        var contest = contests.findById(id).orElse(null);
        if (contest == null)
            return ResponseEntity.notFound().build();
        if ((!contest.isPublic() || "DRAFT".equals(contest.getLifecycleState())) && !isManager(authorization)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(view(contest));
    }

    /**
     * 修改比赛配置：仅 manager 可调用，且仅允许在草稿/就绪状态下修改规则与时间。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @param request       待更新字段，为空的字段保持不变
     * @return 更新后的比赛视图，或 404/409/400
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id,
            @RequestBody
            ContestRequest request) {
        requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null);
        if (item == null)
            return ResponseEntity.notFound().build();
        // 发布后修改规则会破坏快照/审计一致性，故只允许 HIDDEN_STATES 内修改
        if (!HIDDEN_STATES.contains(item.getLifecycleState()))
            return ResponseEntity.status(409).body(Map.of("error", "比赛发布后不能直接修改规则或时间"));
        String type = request.contestType() == null ? null : normalizeType(request.contestType());
        if (request.contestType() != null && type == null)
            return ResponseEntity.badRequest().body(Map.of("error", "比赛模式仅支持 ACM 或 OI"));
        LocalDateTime start = request.startTime() != null ? request.startTime() : item.getStartTime();
        LocalDateTime end = request.endTime() != null ? request.endTime() : item.getEndTime();
        LocalDateTime freeze = request.freezeTime() != null ? request.freezeTime() : item.getFreezeTime();
        String invalid = validateTimes(start, end, freeze);
        if (invalid != null)
            return ResponseEntity.badRequest().body(Map.of("error", invalid));
        item.updateConfig(request.title(), request.description(), type, request.startTime(), request.endTime(),
                request.freezeTime(),
                request.freezeTime() != null || item.getFreezeTime() == null, request.penaltyTime(),
                inferStatus(start, end));
        return ResponseEntity.ok(view(contests.save(item)));
    }

    /**
     * 管理端比赛列表：返回全部比赛（含草稿/取消），仅 manager 可见。
     *
     * @param authorization 认证头
     * @return 比赛视图列表
     */
    @GetMapping("/manage")
    public ResponseEntity<?> manage(@RequestHeader(value = "Authorization", required = false)
    String authorization) {
        requireManager(authorization);
        return ResponseEntity.ok(contests.findAll().stream()
                .sorted(java.util.Comparator.comparing(Contest::getStartTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::view).toList());
    }

    /**
     * 删除比赛：仅 manager，且仅允许删除草稿；已发布比赛必须保留审计记录，只能取消。
     * 删除时级联清理判题、outbox、提交、测试数据、题目与参赛者。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 成功标记，或 404/409/503
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        requireManager(authorization);
        var contest = contests.findForUpdateById(id).orElse(null);
        if (contest == null)
            return ResponseEntity.notFound().build();
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

    /**
     * 发布比赛：仅 manager。要求已设置起止时间、至少一道题，且所有题目参考答案验证通过、
     * 每道题都含隐藏测试数据；随后为每题生成内容寻址题包并切换生命周期。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 发布后的比赛视图，或 400/404/409
     */
    @PostMapping("/{id}/publish")
    @Transactional
    public ResponseEntity<?> publish(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        var user = requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null);
        if (item == null)
            return ResponseEntity.notFound().build();
        if (!HIDDEN_STATES.contains(item.getLifecycleState()))
            return ResponseEntity.status(409).body(Map.of("error", "比赛已发布，不能再次发布"));
        if (item.getStartTime() == null || item.getEndTime() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "发布比赛必须设置开始和结束时间"));
        var contestProblems = problems.findByContest_IdOrderBySortOrderAscIdAsc(id);
        if (contestProblems.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "发布比赛至少需要一道题目"));
        // 发布前逐题校验：参考答案必须验证通过，且必须包含隐藏测试点
        for (var problem : contestProblems) {
            if (!"VALID".equalsIgnoreCase(problem.getValidationStatus())) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "题目 " + problem.getProblemIndex() + " 的参考答案尚未验证通过"));
            }
            boolean hasHidden = testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId()).stream()
                    .anyMatch(t -> !t.isSample());
            if (!hasHidden)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "题目 " + problem.getProblemIndex() + " 缺少隐藏测试数据"));
        }
        // 为每道题生成并绑定内容寻址题包摘要，保证发布内容可追溯
        for (var problem : contestProblems) {
            problem.attachPackage(packageService.publish(problem, user.getId()).getDigest());
            problems.save(problem);
        }
        item.publish();
        // 发布即请求重建榜单快照
        item.requestScoreboardRefresh();
        return ResponseEntity.ok(view(contests.save(item)));
    }

    /**
     * 取消比赛：仅 manager，已结算（FINALIZED）的比赛不可取消。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 取消后的比赛视图，或 404/409
     */
    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancel(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        requireManager(authorization);
        var item = contests.findForUpdateById(id).orElse(null);
        if (item == null)
            return ResponseEntity.notFound().build();
        if ("FINALIZED".equals(item.getLifecycleState()))
            return ResponseEntity.status(409).body(Map.of("error", "最终榜已结算，不能取消"));
        item.cancel();
        return ResponseEntity.ok(view(contests.save(item)));
    }

    /**
     * 报名比赛：任意已登录用户；仅 SCHEDULED/RUNNING 的公开比赛可报名，且必须在开始前完成，
     * 开赛后名单锁定；重复报名幂等返回。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 报名结果，或 404/409
     */
    @PostMapping("/{id}/join")
    @Transactional
    public ResponseEntity<?> join(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        var user = currentUser.require(authorization);
        var contest = contests.findForUpdateById(id).orElse(null);
        if (contest == null)
            return ResponseEntity.notFound().build();
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isPresent()) {
            // 幂等：已报名直接返回成功
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
        // 记录报名事件，供事件流与审计回溯
        events.save(ContestEvent.of(contest, "entry", "jury", "{\"entry_id\":" + user.getId() + "}"));
        return ResponseEntity.status(201).body(Map.of("success", true, "message", "报名成功"));
    }

    /**
     * 比赛题面列表：使用参赛者视图，不暴露参考答案与校验信息。
     *
     * @param id 比赛 ID
     * @return 题面列表或 404
     */
    @GetMapping("/{id}/problems")
    public ResponseEntity<?> problemList(@PathVariable
    Integer id) {
        if (!contests.existsById(id))
            return ResponseEntity.notFound().build();
        return ResponseEntity
                .ok(problems.findByContest_IdOrderBySortOrderAscIdAsc(id).stream().map(this::publicProblem).toList());
    }

    /**
     * 单题题面（限定比赛）：校验题目归属后返回参赛者视图。
     *
     * @param id        比赛 ID
     * @param problemId 题目 ID
     * @return 题面或 404
     */
    @GetMapping("/{id}/problems/{problemId}")
    public ResponseEntity<?> problem(@PathVariable
    Integer id, @PathVariable
    Integer problemId) {
        return problems.findByIdAndContest_Id(problemId, id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(publicProblem(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 单题题面（全局按题目 ID）：不绑定比赛路径，返回参赛者视图。
     *
     * @param problemId 题目 ID
     * @return 题面或 404
     */
    @GetMapping("/problems/{problemId}")
    public ResponseEntity<?> problemGlobal(@PathVariable
    Integer problemId) {
        return problems.findById(problemId).<ResponseEntity<?>>map(p -> ResponseEntity.ok(publicProblem(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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
            if (raw == null || raw.isBlank())
                return List.of();
            return mapper.readValue(raw,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 提交解答：要求已报名、比赛处于可提交阶段且在当前时间窗口内；
     * 通过 Idempotency-Key 去重，提交落库后写入判题 outbox 由派发器异步入队。
     *
     * @param authorization  认证头
     * @param idempotencyKey 幂等键，可空
     * @param id             比赛 ID
     * @param problemId      题目 ID
     * @param request        代码与语言
     * @return 201 与提交视图（含是否幂等重放），或 403/404/409
     */
    @PostMapping("/{id}/problems/{problemId}/submit")
    @Transactional
    public ResponseEntity<?> submit(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @PathVariable
            Integer id, @PathVariable
            Integer problemId, @Valid @RequestBody
            SubmitRequest request) {
        var user = currentUser.require(authorization);
        var contest = contests.findForUpdateById(id).orElse(null);
        if (contest == null)
            return ResponseEntity.notFound().build();
        // 阶段与时间双重校验，封榜阶段仍允许提交（但非裁判不可见）
        if (!List.of("SCHEDULED", "RUNNING", "FROZEN").contains(contest.getLifecycleState()))
            return ResponseEntity.status(409).body(Map.of("error", "比赛当前不可提交"));
        if (contest.getStartTime() != null && LocalDateTime.now().isBefore(contest.getStartTime()))
            return ResponseEntity.status(409).body(Map.of("error", "比赛尚未开始"));
        if (contest.getEndTime() != null && !LocalDateTime.now().isBefore(contest.getEndTime()))
            return ResponseEntity.status(409).body(Map.of("error", "比赛已经结束"));
        if (participants.findByContest_IdAndUser_Id(id, user.getId()).isEmpty())
            return ResponseEntity.status(403).body(Map.of("error", "请先报名比赛"));
        var problem = problems.findByIdAndContest_Id(problemId, id).orElse(null);
        if (problem == null)
            return ResponseEntity.notFound().build();
        // 幂等键命中已有提交时直接回放，避免重复判题
        if (idempotencyKey != null) {
            var replay = submissions.findByContest_IdAndUser_IdAndIdempotencyKey(id, user.getId(), idempotencyKey);
            if (replay.isPresent())
                return ResponseEntity.status(201).body(submissionView(replay.get(), true));
        }
        String jobId = UUID.randomUUID().toString();
        var submission = com.xauat.oj.core.contest.domain.ContestSubmission.create(contest, user,
                problem, request.code(), request.language(), jobId, idempotencyKey);
        // 冗余写入队伍归属，便于后续按 entry 归并榜单与状态
        submission.setTeamId(teamMembers.findByContest_IdAndUser_Id(id, user.getId())
                .map(com.xauat.oj.core.contest.domain.ContestTeamMember::getTeamId).orElse(null));
        submissions.save(submission);
        // outbox 模式：与提交同事务落库，随后由 ContestOutboxDispatcher 异步投递判题队列
        outboxes.save(com.xauat.oj.core.contest.domain.ContestJudgeOutbox.of(submission));
        return ResponseEntity.status(201).body(submissionView(submission, false));
    }

    /**
     * 查询本人提交结果：仅提交者本人可见；终态结果写入缓存，进行中的状态不缓存。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @param problemId     题目 ID
     * @param submissionId  提交 ID
     * @return 提交视图或 404
     */
    @GetMapping("/{id}/problems/{problemId}/submission/{submissionId}")
    public ResponseEntity<?> submission(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id,
            @PathVariable
            Integer problemId, @PathVariable
            Integer submissionId) {
        var user = currentUser.require(authorization);
        String key = "cache:contest_submission:" + submissionId;
        var cached = submissionCache.get(key);
        if (cached.isPresent())
            return ResponseEntity.ok(cached.get());
        return submissions.findById(submissionId).filter(
                s -> id.equals(s.getContestId()) && user.getId().equals(s.getUserId()) && s.getRejudgeOfId() == null)
                .<ResponseEntity<?>>map(s -> {
                    Map<String, Object> body = submissionView(s, false);
                    // 仅当状态不再变化（非进行中）时缓存，避免脏读
                    if (!List.of("Pending", "Queued", "Judging", "Claimed", "Compiling", "Compiled", "Running",
                            "Checking").contains(s.getStatus()))
                        submissionCache.put(key, body);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 本人（或所在队伍）的赛题状态：按 entry 归并提交，给出每题最近状态与是否已通过。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @return 以题目序号为键的状态映射或 404
     */
    @GetMapping("/{id}/statuses")
    public ResponseEntity<?> statuses(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        if (!contests.existsById(id))
            return ResponseEntity.notFound().build();
        var user = currentUser.require(authorization);
        // 将当前用户解析为 entry（队长），从而聚合整队提交
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
            if ("AC".equalsIgnoreCase(s.getVerdict()) || "Accepted".equalsIgnoreCase(s.getVerdict())
                    || "AC".equalsIgnoreCase(s.getStatus()))
                anyAccepted.put(s.getProblemIndex(), true);
        }
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        latest.forEach((index, status) -> body.put(index,
                Map.of("status", status, "solved", anyAccepted.getOrDefault(index, false))));
        return ResponseEntity.ok(body);
    }

    /**
     * 比赛榜单：裁判/参赛者或已发布的公开比赛可见；根据身份选取快照种类，
     * 通过 ETag（快照负载 SHA-256）支持条件请求 304。
     *
     * @param authorization 认证头
     * @param id            比赛 ID
     * @param ifNoneMatch   客户端缓存的 ETag
     * @return 榜单快照响应、304 或 404
     */
    @GetMapping("/{id}/rankings")
    public ResponseEntity<?> rankings(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id, @RequestHeader(value = "If-None-Match", required = false)
            String ifNoneMatch) {
        var contest = contests.findById(id).orElse(null);
        if (contest == null)
            return ResponseEntity.notFound().build();
        boolean jury = false;
        var user = currentUser.optional(authorization);
        if (user != null)
            jury = "manager".equals(user.getRole()) || "staff".equals(user.getRole());
        boolean participant = user != null && participants.findByContest_IdAndUser_Id(id, user.getId()).isPresent();
        // 可见性：裁判、参赛者，或已发布的公开比赛
        boolean visible = jury || participant || (contest.isPublic()
                && !List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState()));
        if (!visible)
            return ResponseEntity.notFound().build();
        // 快照种类随身份变化：裁判看 LIVE，封榜后普通用户看 PUBLIC_FREEZE
        ContestScoreboardSnapshot snapshot = scoreboard.ensure(contest, scoreboard.kindFor(contest, jury));
        return snapshotResponse(snapshot, ifNoneMatch, scoreboard.bodyOf(snapshot));
    }

    private ResponseEntity<?> snapshotResponse(ContestScoreboardSnapshot snapshot, String ifNoneMatch, Object body) {
        String etag = "\"" + sha256(snapshot.getPayload()) + "\"";
        if (etag.equals(ifNoneMatch))
            return ResponseEntity.status(304).header(HttpHeaders.ETAG, etag).build();
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag)
                .header("X-Scoreboard-Version", String.valueOf(snapshot.getScoreboardVersion())).body(body);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String statusOf(Contest contest) {
        String lifecycle = contest.getLifecycleState();
        if ("FINALIZED".equals(lifecycle) || "CANCELLED".equals(lifecycle))
            return "past";
        if ("DRAFT".equals(lifecycle) || "READY".equals(lifecycle))
            return "upcoming";
        LocalDateTime now = LocalDateTime.now();
        if (contest.getStartTime() != null && now.isBefore(contest.getStartTime()))
            return "upcoming";
        if (contest.getEndTime() != null && !now.isBefore(contest.getEndTime()))
            return "past";
        return "ongoing";
    }

    private boolean isFrozen(Contest contest) {
        if (contest.getFreezeTime() == null || contest.getThawedAt() != null)
            return false;
        if (List.of("DRAFT", "READY", "CANCELLED").contains(contest.getLifecycleState()))
            return false;
        return !LocalDateTime.now().isBefore(contest.getFreezeTime());
    }

    private String inferStatus(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null)
            return "upcoming";
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start))
            return "upcoming";
        if (now.isAfter(end))
            return "past";
        return "ongoing";
    }

    private String validateTimes(LocalDateTime start, LocalDateTime end, LocalDateTime freeze) {
        if (start != null && end != null && !end.isAfter(start))
            return "结束时间必须晚于开始时间";
        if (freeze != null && (start == null || end == null || !freeze.isAfter(start) || !freeze.isBefore(end))) {
            return "封榜时间必须位于开始与结束时间之间";
        }
        return null;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank())
            return "ACM";
        String upper = type.toUpperCase(java.util.Locale.ROOT);
        return List.of("ACM", "OI").contains(upper) ? upper : null;
    }

    private boolean isManager(String authorization) {
        var user = currentUser.optional(authorization);
        return user != null && "manager".equals(user.getRole());
    }

    private com.xauat.oj.core.user.domain.User requireManager(String authorization) {
        var user = currentUser.require(authorization);
        if (!"manager".equals(user.getRole()))
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足");
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

    private Map<String, Object> submissionView(com.xauat.oj.core.contest.domain.ContestSubmission item,
            boolean replay) {
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

    /** 创建/更新比赛的请求体，字段与 JSON 名称通过注解映射。 */
    public record ContestRequest(@NotBlank @jakarta.validation.constraints.Size(max = 200)
    String title,
            @NotBlank @jakarta.validation.constraints.Size(max = 131072)
            String description,
            @JsonProperty("contest_type") @JsonAlias("contestType")
            String contestType,
            @JsonProperty("start_time") @JsonAlias("startTime")
            LocalDateTime startTime,
            @JsonProperty("end_time") @JsonAlias("endTime")
            LocalDateTime endTime,
            @JsonProperty("freeze_time") @JsonAlias("freezeTime")
            LocalDateTime freezeTime,
            @JsonProperty("penalty_time") @JsonAlias("penaltyTime")
            Integer penaltyTime) {
    }
    /** 提交请求体：源码与语言。 */
    public record SubmitRequest(@NotBlank @jakarta.validation.constraints.Size(max = 131072)
    String code,
            @NotBlank @jakarta.validation.constraints.Size(max = 50)
            String language) {
    }
}
