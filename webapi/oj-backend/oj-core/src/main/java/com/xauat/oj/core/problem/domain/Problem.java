package com.xauat.oj.core.problem.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 公共题库题目实体，对应数据库表 {@code problems}。
 *
 * <p>非比赛场景下的常规题库题目，含题面、格式说明与时空限制；
 * 与比赛题目 {@code ContestProblem} 分离管理。</p>
 */
@Entity
@Table(name = "problems")
public class Problem extends BaseEntity {
    /** 题目标题，最长 200 字符。 */
    @Column(nullable = false, length = 200)
    private String title;
    /** 题面描述。 */
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    /** 输入格式说明，默认空串。 */
    @Column(name = "input_desc", nullable = false, columnDefinition = "text")
    private String inputDesc = "";
    /** 输出格式说明，默认空串。 */
    @Column(name = "output_desc", nullable = false, columnDefinition = "text")
    private String outputDesc = "";
    /** 难度，默认“简单”。 */
    @Column(nullable = false, length = 20)
    private String difficulty = "简单";
    /** 时间限制（毫秒），默认 1000。 */
    @Column(name = "time_limit", nullable = false)
    private int timeLimit = 1000;
    /** 内存限制（MB），默认 256。 */
    @Column(name = "memory_limit", nullable = false)
    private int memoryLimit = 256;
    /** 创建者用户 ID。 */
    @Column(name = "created_by")
    private Integer createdBy;
    /** 是否公开；false 时仅创建者/管理员可见。 */
    @Column(name = "is_public", nullable = false)
    private boolean publicProblem = true;

    protected Problem() {
    }

    /** 返回题目标题。 */
    public String getTitle() {
        return title;
    }
    /** 返回题面描述。 */
    public String getDescription() {
        return description;
    }
    /** 返回输入格式说明。 */
    public String getInputDesc() {
        return inputDesc;
    }
    /** 返回输出格式说明。 */
    public String getOutputDesc() {
        return outputDesc;
    }
    /** 返回难度。 */
    public String getDifficulty() {
        return difficulty;
    }
    /** 返回时间限制（毫秒）。 */
    public int getTimeLimit() {
        return timeLimit;
    }
    /** 返回内存限制（MB）。 */
    public int getMemoryLimit() {
        return memoryLimit;
    }
    /** 是否公开。 */
    public boolean isPublicProblem() {
        return publicProblem;
    }
    /** 返回题目 ID。 */
    public Integer getId() {
        return super.getId();
    }
}
