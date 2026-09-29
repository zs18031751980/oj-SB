package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "contest_packages")
public class ContestPackage {
    @Id @Column(length = 64) private String digest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private ContestProblem problem;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "actor_id") private Integer actorId;
    @Column(name = "validation_state") private String validationState = "VALID";
    @Column(name = "validation_error", columnDefinition = "text") private String validationError;
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected ContestPackage() {}
    public static ContestPackage create(String digest, ContestProblem problem, Integer actorId, String payload) { ContestPackage item = new ContestPackage(); item.digest = digest; item.problem = problem; item.actorId = actorId; item.payload = payload; item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    public String getDigest() { return digest; }
    public Integer getProblemId() { return problem.getId(); }
    public String getPayload() { return payload; }
    public String getValidationState() { return validationState; }
    public void replacePayload(String payload, Integer actorId) { this.payload = payload; this.actorId = actorId; this.validationState = "VALID"; this.validationError = null; this.updatedAt = java.time.LocalDateTime.now(); }
}
