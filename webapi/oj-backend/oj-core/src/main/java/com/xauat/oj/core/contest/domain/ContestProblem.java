package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_problems")
public class ContestProblem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @Column(name = "problem_index", nullable = false, length = 10) private String problemIndex;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "input_desc", columnDefinition = "text") private String inputDesc = "";
    @Column(name = "output_desc", columnDefinition = "text") private String outputDesc = "";
    @Column(name = "correct_answer", nullable = false, columnDefinition = "text") private String correctAnswer;
    @Column(name = "time_limit") private int timeLimit = 1000;
    @Column(name = "memory_limit") private int memoryLimit = 256;
    @Column(length = 20) private String difficulty = "中等";
    @Column(length = 20) private String language = "cpp";
    @Column(columnDefinition = "text") private String samples = "[]";
    private int score = 100;
    @Column(name = "sort_order") private int sortOrder;
    @Column(name = "validation_version") private int validationVersion = 1;
    @Column(name = "validation_status", length = 20) private String validationStatus = "PENDING";
    @Column(name = "validation_error", columnDefinition = "text") private String validationError;
    @Column(name = "checker_config", columnDefinition = "text") private String checkerConfig = "{\"checker\":\"text\"}";
    @Column(name = "package_digest", length = 64) private String packageDigest;
    protected ContestProblem() {}
    public Integer getId() { return super.getId(); }
    public String getProblemIndex() { return problemIndex; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getInputDesc() { return inputDesc == null ? "" : inputDesc; }
    public String getOutputDesc() { return outputDesc == null ? "" : outputDesc; }
    public int getTimeLimit() { return timeLimit; }
    public int getMemoryLimit() { return memoryLimit; }
    public String getDifficulty() { return difficulty; }
    public String getSamples() { return samples == null ? "[]" : samples; }
    public Integer getContestId() { return contest == null ? null : contest.getId(); }
    public String getCheckerConfig() { return checkerConfig; }
    public String getPackageDigest() { return packageDigest; }
    public int getValidationVersion() { return validationVersion; }
    public String getValidationStatus() { return validationStatus; }
    public void attachPackage(String digest) { this.packageDigest = digest; }
    public void markValidated() { this.validationStatus = "VALID"; this.validationError = null; }
    public void updateCheckerConfig(String checkerConfig) { this.checkerConfig = checkerConfig == null || checkerConfig.isBlank() ? "{\"checker\":\"text\"}" : checkerConfig; }
}
