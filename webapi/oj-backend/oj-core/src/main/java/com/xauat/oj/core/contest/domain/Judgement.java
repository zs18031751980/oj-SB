package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "judgements")
public class Judgement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id") private ContestSubmission submission;
    @Column(name = "attempt_id", nullable = false) private int attemptId;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "package_digest", length = 64) private String packageDigest;
    @Column(name = "batch_id") private Integer batchId;
    protected Judgement() {}
    public static Judgement of(ContestSubmission submission, int attemptId, String status, String payload) { Judgement item = new Judgement(); item.submission = submission; item.attemptId = attemptId; item.status = status; item.payload = payload == null ? "{}" : payload; return item; }
    public static Judgement of(ContestSubmission submission, int attemptId, String status, String payload, String packageDigest, Integer batchId) { Judgement item = of(submission, attemptId, status, payload); item.packageDigest = packageDigest; item.batchId = batchId; return item; }
    public int getAttemptId() { return attemptId; }
}
