package com.xauat.oj.core.ranking.domain;

import jakarta.persistence.*;

/**
 * 用户判题统计实体，对应数据库表 {@code user_judge_stats}。
 *
 * <p>汇总用户的排名、解决题数、积分（rating）及按难度（简单/中等/困难）的解题数，
 * 作为全局排行榜的投影结果。主键即用户 ID。</p>
 */
@Entity
@Table(name = "user_judge_stats")
public class UserJudgeStats {
    /** 用户 ID，同时作为主键。 */
    @Id
    @Column(name = "user_id")
    private Integer userId;
    /** 全局排名。 */
    private int rank;
    /** 已解决题目总数。 */
    @Column(name = "solved_count")
    private int solvedCount;
    /** 积分/等级分。 */
    private int rating;
    /** 简单题解决数。 */
    @Column(name = "easy_count")
    private int easyCount;
    /** 中等题解决数。 */
    @Column(name = "medium_count")
    private int mediumCount;
    /** 困难题解决数。 */
    @Column(name = "hard_count")
    private int hardCount;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;
    protected UserJudgeStats() {
    }
    /** 返回用户 ID。 */
    public Integer getUserId() {
        return userId;
    }
    /** 返回排名。 */
    public int getRank() {
        return rank;
    }
    /** 返回解决题数。 */
    public int getSolvedCount() {
        return solvedCount;
    }
    /** 返回积分。 */
    public int getRating() {
        return rating;
    }
}
