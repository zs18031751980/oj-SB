package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "reference_validation_jobs")
public class ReferenceValidationJob {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private ContestProblem problem;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 20) private String state = "PENDING";
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected ReferenceValidationJob() {}
    public static ReferenceValidationJob create(String id, ContestProblem problem) { ReferenceValidationJob item = new ReferenceValidationJob(); item.id = id; item.problem = problem; item.version = problem.getValidationVersion(); item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    public String getId() { return id; }
    public Integer getProblemId() { return problem == null ? null : problem.getId(); }
    public int getVersion() { return version; }
    public String getState() { return state; }
    public void markQueued() { state = "QUEUED"; updatedAt = java.time.LocalDateTime.now(); }
}
