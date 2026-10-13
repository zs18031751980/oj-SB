package com.xauat.oj.core.submission.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.problem.domain.Problem;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 公共题库提交实体，对应数据库表 {@code submissions}。
 *
 * <p>记录用户对公共题目的代码提交与判题结果。
 * {@code status} 取值示例：Pending（排队）、Judging（判题中）、AC/WA/TLE/MLE/RE/CE 等结论。</p>
 */
@Entity
@Table(name = "submissions")
public class Submission extends BaseEntity {
    /** 提交用户。 */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    /** 所提交的题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private Problem problem;
    /** 提交源代码。 */
    @Column(nullable = false, columnDefinition = "text") private String code;
    /** 提交语言。 */
    @Column(nullable = false, length = 50) private String language;
    /** 提交/判题状态，默认 Pending。 */
    @Column(nullable = false, length = 20) private String status = "Pending";
    /** 耗时（毫秒）。 */
    @Column(name = "time_used") private Integer timeUsed;
    /** 内存占用（KB）。 */
    @Column(name = "memory_used") private Integer memoryUsed;
    /** 各测试点结果（JSON 文本）。 */
    @Column(name = "testcase_results", columnDefinition = "text") private String testcaseResults;
    /** 首个失败测试点的序号。 */
    @Column(name = "fail_testcase_index") private Integer failTestcaseIndex;
    /** 判题任务 ID。 */
    @Column(name = "job_id", unique = true, length = 64) private String jobId;
    /** 尝试次数（重判时递增）。 */
    @Column(name = "attempt_id", nullable = false) private int attemptId;
    /** 幂等键，防止重复提交。 */
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    protected Submission() {}
    /** 创建提交记录。 */
    public static Submission create(User user, Problem problem, String code, String language, String jobId, String idempotencyKey) {
        Submission submission = new Submission(); submission.user = user; submission.problem = problem; submission.code = code;
        submission.language = language; submission.jobId = jobId; submission.idempotencyKey = idempotencyKey; return submission;
    }
    /** 返回状态。 */
    public String getStatus() { return status; }
    /** 返回语言。 */
    public String getLanguage() { return language; }
    /** 返回判题任务 ID。 */
    public String getJobId() { return jobId; }
    /** 返回题目 ID。 */
    public Integer getProblemId() { return problem == null ? null : problem.getId(); }
    /** 返回题目实体。 */
    public Problem getProblem() { return problem; }
    /** 返回用户 ID。 */
    public Integer getUserId() { return user == null ? null : user.getId(); }
    /** 返回源代码。 */
    public String getCode() { return code; }
    /** 标记为判题中。 */
    public void markRunning() { status = "Judging"; }
    /** 标记判题结束，写入结论。 */
    public void markFinished(String verdict) { status = verdict; }
    /** 记录判题结果：结论、耗时、内存、测试点结果与失败测试点序号。 */
    public void recordResult(String verdict, Integer timeUsed, Integer memoryUsed, String testcaseResults, Integer failIndex) { status = verdict; this.timeUsed = timeUsed; this.memoryUsed = memoryUsed; this.testcaseResults = testcaseResults; this.failTestcaseIndex = failIndex; }
    /** 返回耗时。 */
    public Integer getTimeUsed() { return timeUsed; }
    /** 返回内存占用。 */
    public Integer getMemoryUsed() { return memoryUsed; }
    /** 返回测试点结果。 */
    public String getTestcaseResults() { return testcaseResults; }
    /** 返回失败测试点序号。 */
    public Integer getFailTestcaseIndex() { return failTestcaseIndex; }
}
