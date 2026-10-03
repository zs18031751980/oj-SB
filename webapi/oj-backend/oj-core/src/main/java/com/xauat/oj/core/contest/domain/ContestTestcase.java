package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_testcases")
public class ContestTestcase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_problem_id") private ContestProblem contestProblem;
    @Column(name = "input_data", nullable = false, columnDefinition = "text") private String inputData;
    @Column(name = "expected_output", nullable = false, columnDefinition = "text") private String expectedOutput;
    @Column(name = "is_sample") private boolean sample;
    @Column(name = "sort_order") private int sortOrder;
    protected ContestTestcase() {}
    public static ContestTestcase create(ContestProblem problem, String inputData, String expectedOutput, boolean sample, int sortOrder) {
        ContestTestcase item = new ContestTestcase();
        item.contestProblem = problem; item.inputData = inputData; item.expectedOutput = expectedOutput;
        item.sample = sample; item.sortOrder = sortOrder;
        return item;
    }
    public Integer getContestProblemId() { return contestProblem == null ? null : contestProblem.getId(); }
    public boolean isSample() { return sample; }
    public String getInputData() { return inputData; }
    public String getExpectedOutput() { return expectedOutput; }
    public int getSortOrder() { return sortOrder; }
}
