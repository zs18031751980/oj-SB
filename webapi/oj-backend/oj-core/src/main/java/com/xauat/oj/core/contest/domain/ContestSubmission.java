package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 比赛提交实体，对应数据库表 {@code contest_submissions}。
 *
 * <p>记录参赛者针对某道比赛题的代码提交及判题全过程。
 * {@code status}/{@code verdict} 取值示例：Pending（排队）、Judging（判题中）、
 * AC（通过）、WA（答案错误）、TLE（超时）、MLE（超内存）、RE（运行错误）、CE（编译错误）等。</p>
 *
 * <p>{@code contestEligible} 标记该提交是否计入比赛成绩（练习模式为 false）。</p>
 */
@Entity
@Table(name = "contest_submissions")
public class ContestSubmission extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 提交用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    /** 所提交的比赛题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_problem_id")
    private ContestProblem contestProblem;
    /** 题目序号快照（如 A、B）。 */
    @Column(name = "problem_index", length = 10)
    private String problemIndex = "";
    /** 提交状态，默认 Pending。 */
    @Column(length = 32)
    private String status = "Pending";
    /** 最终判题结论（如 AC/WA/TLE）。 */
    @Column(length = 32)
    private String verdict;
    /** 通过测试点数量。 */
    private int passed;
    /** 测试点总数。 */
    private int total;
    /** 得分。 */
    private int score;
    /** 提交语言，默认 cpp。 */
    @Column(length = 20)
    private String language = "cpp";
    /** 提交源代码。 */
    @Column(columnDefinition = "text")
    private String code = "";
    /** 判题系统侧的提交 ID。 */
    @Column(name = "judge_submission_id", unique = true, length = 64)
    private String judgeSubmissionId;
    /** 本平台生成的判题任务 ID。 */
    @Column(name = "job_id", unique = true, length = 64)
    private String jobId;
    /** 评测尝试次数（重判时递增），默认 1。 */
    @Column(name = "attempt_id")
    private int attemptId = 1;
    /** 处理该提交的判题工作节点标识。 */
    @Column(name = "worker_id", length = 128)
    private String workerId;
    /** 入队时间。 */
    @Column(name = "queued_at")
    private LocalDateTime queuedAt;
    /** 开始判题时间。 */
    @Column(name = "judge_started_at")
    private LocalDateTime judgeStartedAt;
    /** 判题结束时间。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    /** CPU 耗时（毫秒）。 */
    private Integer cpuTime;
    /** 墙钟耗时（毫秒）。 */
    private Integer wallTime;
    /** 峰值内存占用（KB）。 */
    private Long memory;
    /** 各测试点结果（JSON 文本）。 */
    @Column(name = "testcase_results", columnDefinition = "text")
    private String testcaseResults;
    /** 错误信息（编译/运行错误详情等）。 */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    /** 开始编译时间。 */
    @Column(name = "compile_started_at")
    private LocalDateTime compileStartedAt;
    /** 编译完成时间。 */
    @Column(name = "compile_finished_at")
    private LocalDateTime compileFinishedAt;
    /** 开始执行时间。 */
    @Column(name = "execution_started_at")
    private LocalDateTime executionStartedAt;
    /** 执行完成时间。 */
    @Column(name = "execution_finished_at")
    private LocalDateTime executionFinishedAt;
    /** 判题结果回写时间。 */
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
    /** 输出字节数。 */
    @Column(name = "output_size")
    private Integer outputSize;
    /** 进程退出码。 */
    @Column(name = "exit_code")
    private Integer exitCode;
    /** 导致终止的信号编号。 */
    @Column(name = "signal")
    private Integer signal;
    /** 本次评测使用的题包摘要。 */
    @Column(name = "package_digest", length = 64)
    private String packageDigest;
    /** 判题请求摘要（用于校验一致性）。 */
    @Column(name = "request_digest", length = 64)
    private String requestDigest;
    /** 幂等键，防止重复提交。 */
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    /** 判题结果接收时间。 */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
    /** 是否计入比赛成绩（false 为练习提交）。 */
    @Column(name = "contest_eligible")
    private boolean contestEligible = true;
    /** 提交时间。 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    /** 若为重判，指向被重判的原始提交。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejudge_of")
    private ContestSubmission rejudgeOf;
    /** 重判时原始提交的尝试次数。 */
    @Column(name = "rejudge_base_attempt")
    private Integer rejudgeBaseAttempt;
    protected ContestSubmission() {
    }
    /** 创建比赛提交：复制题目序号并初始化入队/提交时间。 */
    public static ContestSubmission create(Contest contest, com.xauat.oj.core.user.domain.User user,
            ContestProblem problem,
            String code, String language, String jobId, String idempotencyKey) {
        ContestSubmission item = new ContestSubmission();
        item.contest = contest;
        item.user = user;
        item.contestProblem = problem;
        item.problemIndex = problem.getProblemIndex();
        item.code = code;
        item.language = language;
        item.jobId = jobId;
        item.idempotencyKey = idempotencyKey;
        item.queuedAt = LocalDateTime.now();
        item.submittedAt = item.queuedAt;
        return item;
    }
    /** 基于原提交创建重判提交：沿用代码与资格，尝试次数加一并指向原提交。 */
    public static ContestSubmission rejudgeOf(ContestSubmission source, String jobId, String batchKey) {
        ContestSubmission item = new ContestSubmission();
        item.contest = source.contest;
        item.user = source.user;
        item.contestProblem = source.contestProblem;
        item.problemIndex = source.problemIndex;
        item.code = source.code;
        item.language = source.language;
        item.jobId = jobId;
        item.idempotencyKey = batchKey;
        item.attemptId = source.attemptId + 1;
        item.queuedAt = LocalDateTime.now();
        item.submittedAt = source.submittedAt;
        item.contestEligible = source.contestEligible;
        item.rejudgeOf = source;
        item.rejudgeBaseAttempt = source.attemptId;
        return item;
    }
    public int getAttemptId() {
        return attemptId;
    }
    public Integer getContestProblemId() {
        return contestProblem == null ? null : contestProblem.getId();
    }
    public Integer getId() {
        return super.getId();
    }
    public Integer getContestId() {
        return contest == null ? null : contest.getId();
    }
    public Integer getUserId() {
        return user == null ? null : user.getId();
    }
    public String getStatus() {
        return status;
    }
    public String getVerdict() {
        return verdict;
    }
    public String getJobId() {
        return jobId;
    }
    public String getLanguage() {
        return language;
    }
    public String getProblemIndex() {
        return problemIndex;
    }
    public String getCode() {
        return code;
    }
    public int getPassed() {
        return passed;
    }
    public int getTotal() {
        return total;
    }
    public int getScore() {
        return score;
    }
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    public LocalDateTime getReceivedAt() {
        return receivedAt == null ? submittedAt : receivedAt;
    }
    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
    public Integer getRejudgeOfId() {
        return rejudgeOf == null ? null : rejudgeOf.getId();
    }
    public Integer getRejudgeBaseAttempt() {
        return rejudgeBaseAttempt;
    }
    public String getTestcaseResults() {
        return testcaseResults;
    }
    public Integer getCpuTime() {
        return cpuTime;
    }
    public Long getMemory() {
        return memory;
    }
    public Integer getWallTime() {
        return wallTime;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public boolean isContestEligible() {
        return contestEligible;
    }
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public void asPractice() {
        this.contestEligible = false;
    }
    public LocalDateTime getCompileStartedAt() {
        return compileStartedAt;
    }
    public LocalDateTime getCompileFinishedAt() {
        return compileFinishedAt;
    }
    public LocalDateTime getExecutionStartedAt() {
        return executionStartedAt;
    }
    public LocalDateTime getExecutionFinishedAt() {
        return executionFinishedAt;
    }
    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }
    public Integer getOutputSize() {
        return outputSize;
    }
    public Integer getExitCode() {
        return exitCode;
    }
    public Integer getSignal() {
        return signal;
    }
    public String getPackageDigest() {
        return packageDigest;
    }
    public String getRequestDigest() {
        return requestDigest;
    }
    /** 记录编译/执行各阶段时间及输出大小、退出码、信号、题包摘要等运行指标。 */
    public void recordMetrics(LocalDateTime compileStart, LocalDateTime compileEnd, LocalDateTime executionStart,
            LocalDateTime executionEnd, Integer outputSize, Integer exitCode, Integer signal, String packageDigest) {
        this.compileStartedAt = compileStart;
        this.compileFinishedAt = compileEnd;
        this.executionStartedAt = executionStart;
        this.executionFinishedAt = executionEnd;
        this.checkedAt = executionEnd;
        this.outputSize = outputSize;
        this.exitCode = exitCode;
        this.signal = signal;
        this.packageDigest = packageDigest;
    }
    /** 标记为判题中，记录工作节点与开始时间。 */
    public void markRunning(String workerId) {
        status = "Judging";
        this.workerId = workerId;
        judgeStartedAt = LocalDateTime.now();
    }
    /** 标记判题结束，写入结论与完成时间。 */
    public void markFinished(String verdict) {
        status = verdict;
        this.verdict = verdict;
        finishedAt = LocalDateTime.now();
    }
    /** 人工改判为指定结论（分数默认 0）。 */
    public void overrideVerdict(String verdict) {
        overrideVerdict(verdict, 0);
    }
    /** 人工改判：AC 时通过数记为总数，否则为 0，并清空测试点结果与错误信息、递增尝试次数。 */
    public void overrideVerdict(String verdict, int score) {
        boolean accepted = "AC".equalsIgnoreCase(verdict);
        this.status = verdict;
        this.verdict = verdict;
        this.score = score;
        this.passed = accepted ? total : 0;
        this.testcaseResults = null;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
        this.attemptId++;
    }
    /** 将复判候选结果写回原提交，并把 attempt 推进到候选版本。 */
    public void applyCandidate(ContestSubmission candidate) {
        this.status = candidate.status;
        this.verdict = candidate.verdict;
        this.passed = candidate.passed;
        this.total = candidate.total;
        this.score = candidate.score;
        this.cpuTime = candidate.cpuTime;
        this.wallTime = candidate.wallTime;
        this.memory = candidate.memory;
        this.testcaseResults = candidate.testcaseResults;
        this.attemptId = candidate.attemptId;
        this.finishedAt = LocalDateTime.now();
    }
    /** 重置为排队状态。 */
    public void markQueued() {
        this.status = "Pending";
    }
    /** 一次性记录判题结论、耗时、内存、测试点结果与通过数。 */
    public void recordResult(String verdict, Integer cpuTime, Integer memory, String testcaseResults, int passed,
            int total) {
        this.status = verdict;
        this.verdict = verdict;
        this.cpuTime = cpuTime;
        this.memory = memory == null ? null : memory.longValue();
        this.testcaseResults = testcaseResults;
        this.passed = passed;
        this.total = total;
        this.finishedAt = LocalDateTime.now();
    }
}
