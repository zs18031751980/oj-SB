package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_judge_outbox")
public class ContestJudgeOutbox extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id", unique = true) private ContestSubmission submission;
    @Column(nullable = false, length = 20) private String state = "PENDING";
    @Column(name = "dispatch_attempts") private int dispatchAttempts;
    @Column(name = "last_error", columnDefinition = "text") private String lastError;
    @Column(name = "dispatched_at") private java.time.LocalDateTime dispatchedAt;
    protected ContestJudgeOutbox() {}
    public static ContestJudgeOutbox of(ContestSubmission submission) { ContestJudgeOutbox item = new ContestJudgeOutbox(); item.submission = submission; return item; }
    public ContestSubmission getSubmission() { return submission; }
    public String getState() { return state; }
    public void markDispatched() { state = "DISPATCHED"; dispatchedAt = java.time.LocalDateTime.now(); }
    public void markFailed(String error) { state = "PENDING"; dispatchAttempts++; lastError = error; }
}
