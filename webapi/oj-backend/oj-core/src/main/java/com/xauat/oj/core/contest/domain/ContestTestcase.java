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
    public Integer getContestProblemId() { return contestProblem == null ? null : contestProblem.getId(); }
    public String getInputData() { return inputData; }
    public String getExpectedOutput() { return expectedOutput; }
    public int getSortOrder() { return sortOrder; }
}
