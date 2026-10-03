package com.xauat.oj.core.submission.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.problem.domain.Problem;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "submissions")
public class Submission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private Problem problem;
    @Column(nullable = false, columnDefinition = "text") private String code;
    @Column(nullable = false, length = 50) private String language;
    @Column(nullable = false, length = 20) private String status = "Pending";
    @Column(name = "time_used") private Integer timeUsed;
    @Column(name = "memory_used") private Integer memoryUsed;
    @Column(name = "testcase_results", columnDefinition = "text") private String testcaseResults;
    @Column(name = "fail_testcase_index") private Integer failTestcaseIndex;
    @Column(name = "job_id", unique = true, length = 64) private String jobId;
    @Column(name = "attempt_id", nullable = false) private int attemptId;
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    protected Submission() {}
    public static Submission create(User user, Problem problem, String code, String language, String jobId, String idempotencyKey) {
        Submission submission = new Submission(); submission.user = user; submission.problem = problem; submission.code = code;
        submission.language = language; submission.jobId = jobId; submission.idempotencyKey = idempotencyKey; return submission;
    }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public String getJobId() { return jobId; }
    public Integer getProblemId() { return problem == null ? null : problem.getId(); }
    public Problem getProblem() { return problem; }
    public Integer getUserId() { return user == null ? null : user.getId(); }
    public String getCode() { return code; }
    public void markRunning() { status = "Judging"; }
    public void markFinished(String verdict) { status = verdict; }
    public void recordResult(String verdict, Integer timeUsed, Integer memoryUsed, String testcaseResults, Integer failIndex) { status = verdict; this.timeUsed = timeUsed; this.memoryUsed = memoryUsed; this.testcaseResults = testcaseResults; this.failTestcaseIndex = failIndex; }
    public Integer getTimeUsed() { return timeUsed; }
    public Integer getMemoryUsed() { return memoryUsed; }
    public String getTestcaseResults() { return testcaseResults; }
    public Integer getFailTestcaseIndex() { return failTestcaseIndex; }
}
