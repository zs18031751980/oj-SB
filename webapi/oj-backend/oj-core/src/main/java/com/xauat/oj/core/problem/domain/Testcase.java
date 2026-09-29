package com.xauat.oj.core.problem.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "testcases")
public class Testcase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private Problem problem;
    @Column(name = "input_data", nullable = false, columnDefinition = "text") private String inputData;
    @Column(name = "output_data", nullable = false, columnDefinition = "text") private String outputData;
    @Column(name = "is_sample") private boolean sample;
    @Column(name = "sort_order") private int sortOrder;
    protected Testcase() {}
    public String getInputData() { return inputData; }
    public String getOutputData() { return outputData; }
    public int getSortOrder() { return sortOrder; }
}
