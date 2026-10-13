package com.xauat.oj.core.problem.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 公共题库题目的测试点实体，对应数据库表 {@code testcases}。
 *
 * <p>存储公共题目的输入/输出数据及是否为样例，供判题逐点比对。</p>
 */
@Entity
@Table(name = "testcases")
public class Testcase extends BaseEntity {
    /** 所属题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id")
    private Problem problem;
    /** 测试点输入数据。 */
    @Column(name = "input_data", nullable = false, columnDefinition = "text")
    private String inputData;
    /** 测试点期望输出数据。 */
    @Column(name = "output_data", nullable = false, columnDefinition = "text")
    private String outputData;
    /** 是否为公开样例。 */
    @Column(name = "is_sample")
    private boolean sample;
    /** 测试点顺序。 */
    @Column(name = "sort_order")
    private int sortOrder;
    protected Testcase() {
    }
    /** 返回输入数据。 */
    public String getInputData() {
        return inputData;
    }
    /** 返回期望输出。 */
    public String getOutputData() {
        return outputData;
    }
    /** 返回排序序号。 */
    public int getSortOrder() {
        return sortOrder;
    }
}
