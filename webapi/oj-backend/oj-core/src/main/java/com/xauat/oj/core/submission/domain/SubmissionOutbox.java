package com.xauat.oj.core.submission.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "submission_outbox")
public class SubmissionOutbox extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id", unique = true) private Submission submission;
    @Column(nullable = false, length = 20) private String state = "PENDING";
    @Column(name = "dispatch_attempts", nullable = false) private int dispatchAttempts;
    @Column(name = "last_error", columnDefinition = "text") private String lastError;
    @Column(name = "dispatched_at") private java.time.LocalDateTime dispatchedAt;
    protected SubmissionOutbox() {}
    public static SubmissionOutbox of(Submission submission) { SubmissionOutbox outbox = new SubmissionOutbox(); outbox.submission = submission; return outbox; }
    public Submission getSubmission() { return submission; }
    public String getState() { return state; }
    public void markDispatched() { state = "DISPATCHED"; dispatchedAt = java.time.LocalDateTime.now(); }
    public void markFailed(String error) { state = "PENDING"; dispatchAttempts++; lastError = error; }
}
