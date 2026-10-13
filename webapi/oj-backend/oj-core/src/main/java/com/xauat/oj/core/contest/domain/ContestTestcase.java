package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 比赛题目的测试点实体，对应数据库表 {@code contest_testcases}。
 *
 * <p>存储单个测试点的输入、期望输出及是否为样例，供判题时逐点比对。</p>
 */
@Entity
@Table(name = "contest_testcases")
public class ContestTestcase extends BaseEntity {
    /** 所属比赛题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_problem_id") private ContestProblem contestProblem;
    /** 测试点输入数据。 */
    @Column(name = "input_data", nullable = false, columnDefinition = "text") private String inputData;
    /** 测试点期望输出。 */
    @Column(name = "expected_output", nullable = false, columnDefinition = "text") private String expectedOutput;
    /** 是否为公开样例。 */
    @Column(name = "is_sample") private boolean sample;
    /** 测试点顺序。 */
    @Column(name = "sort_order") private int sortOrder;
    protected ContestTestcase() {}
    /** 创建测试点。 */
    public static ContestTestcase create(ContestProblem problem, String inputData, String expectedOutput, boolean sample, int sortOrder) {
        ContestTestcase item = new ContestTestcase();
        item.contestProblem = problem; item.inputData = inputData; item.expectedOutput = expectedOutput;
        item.sample = sample; item.sortOrder = sortOrder;
        return item;
    }
    /** 返回所属比赛题目 ID。 */
    public Integer getContestProblemId() { return contestProblem == null ? null : contestProblem.getId(); }
    /** 是否为样例。 */
    public boolean isSample() { return sample; }
    /** 返回输入数据。 */
    public String getInputData() { return inputData; }
    /** 返回期望输出。 */
    public String getExpectedOutput() { return expectedOutput; }
    /** 返回排序序号。 */
    public int getSortOrder() { return sortOrder; }
}
