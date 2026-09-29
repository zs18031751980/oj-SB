package com.xauat.oj.core.problem.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "problems")
public class Problem extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "input_desc", nullable = false, columnDefinition = "text")
    private String inputDesc = "";
    @Column(name = "output_desc", nullable = false, columnDefinition = "text")
    private String outputDesc = "";
    @Column(nullable = false, length = 20)
    private String difficulty = "简单";
    @Column(name = "time_limit", nullable = false)
    private int timeLimit = 1000;
    @Column(name = "memory_limit", nullable = false)
    private int memoryLimit = 256;
    @Column(name = "created_by")
    private Integer createdBy;
    @Column(name = "is_public", nullable = false)
    private boolean publicProblem = true;

    protected Problem() {}

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getInputDesc() { return inputDesc; }
    public String getOutputDesc() { return outputDesc; }
    public String getDifficulty() { return difficulty; }
    public int getTimeLimit() { return timeLimit; }
    public int getMemoryLimit() { return memoryLimit; }
    public boolean isPublicProblem() { return publicProblem; }
    public Integer getId() { return super.getId(); }
}
