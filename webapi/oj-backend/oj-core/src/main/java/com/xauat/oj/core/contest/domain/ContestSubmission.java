package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_submissions")
public class ContestSubmission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_problem_id") private ContestProblem contestProblem;
    @Column(name = "problem_index", length = 10) private String problemIndex = "";
    @Column(length = 32) private String status = "Pending";
    @Column(length = 32) private String verdict;
    private int passed;
    private int total;
    private int score;
    @Column(length = 20) private String language = "cpp";
    @Column(columnDefinition = "text") private String code = "";
    @Column(name = "judge_submission_id", unique = true, length = 64) private String judgeSubmissionId;
    @Column(name = "job_id", unique = true, length = 64) private String jobId;
    @Column(name = "attempt_id") private int attemptId = 1;
    @Column(name = "worker_id", length = 128) private String workerId;
    @Column(name = "queued_at") private LocalDateTime queuedAt;
    @Column(name = "judge_started_at") private LocalDateTime judgeStartedAt;
    @Column(name = "finished_at") private LocalDateTime finishedAt;
    private Integer cpuTime;
    private Integer wallTime;
    private Long memory;
    @Column(name = "testcase_results", columnDefinition = "text") private String testcaseResults;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    @Column(name = "received_at") private LocalDateTime receivedAt;
    @Column(name = "contest_eligible") private boolean contestEligible = true;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rejudge_of") private ContestSubmission rejudgeOf;
    @Column(name = "rejudge_base_attempt") private Integer rejudgeBaseAttempt;
    protected ContestSubmission() {}
    public static ContestSubmission create(Contest contest, com.xauat.oj.core.user.domain.User user, ContestProblem problem,
                                           String code, String language, String jobId, String idempotencyKey) {
        ContestSubmission item = new ContestSubmission(); item.contest = contest; item.user = user; item.contestProblem = problem;
        item.problemIndex = problem.getProblemIndex(); item.code = code; item.language = language; item.jobId = jobId;
        item.idempotencyKey = idempotencyKey; item.queuedAt = LocalDateTime.now(); item.submittedAt = item.queuedAt; return item;
    }
    public static ContestSubmission rejudgeOf(ContestSubmission source, String jobId, String batchKey) { ContestSubmission item = new ContestSubmission(); item.contest = source.contest; item.user = source.user; item.contestProblem = source.contestProblem; item.problemIndex = source.problemIndex; item.code = source.code; item.language = source.language; item.jobId = jobId; item.idempotencyKey = batchKey; item.attemptId = source.attemptId + 1; item.queuedAt = LocalDateTime.now(); item.submittedAt = source.submittedAt; item.contestEligible = source.contestEligible; item.rejudgeOf = source; item.rejudgeBaseAttempt = source.attemptId; return item; }
    public int getAttemptId() { return attemptId; }
    public Integer getContestProblemId() { return contestProblem == null ? null : contestProblem.getId(); }
    public Integer getId() { return super.getId(); }
    public Integer getContestId() { return contest == null ? null : contest.getId(); }
    public Integer getUserId() { return user == null ? null : user.getId(); }
    public String getStatus() { return status; }
    public String getVerdict() { return verdict; }
    public String getJobId() { return jobId; }
    public String getLanguage() { return language; }
    public String getProblemIndex() { return problemIndex; }
    public String getCode() { return code; }
    public int getPassed() { return passed; }
    public int getTotal() { return total; }
    public int getScore() { return score; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public Integer getRejudgeOfId() { return rejudgeOf == null ? null : rejudgeOf.getId(); }
    public Integer getRejudgeBaseAttempt() { return rejudgeBaseAttempt; }
    public void markRunning(String workerId) { status = "Judging"; this.workerId = workerId; judgeStartedAt = LocalDateTime.now(); }
    public void markFinished(String verdict) { status = verdict; this.verdict = verdict; finishedAt = LocalDateTime.now(); }
    public void overrideVerdict(String verdict) { this.status = verdict; this.verdict = verdict; this.finishedAt = LocalDateTime.now(); }
    public void recordResult(String verdict, Integer cpuTime, Integer memory, String testcaseResults, int passed, int total) { this.status = verdict; this.verdict = verdict; this.cpuTime = cpuTime; this.memory = memory == null ? null : memory.longValue(); this.testcaseResults = testcaseResults; this.passed = passed; this.total = total; this.finishedAt = LocalDateTime.now(); }
}
