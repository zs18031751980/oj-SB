package com.xauat.oj.core.favorite.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 题目收藏实体，对应数据库表 {@code favorites}。
 *
 * <p>记录用户收藏的题目；通过 {@code (user_id, problem_id)} 唯一约束
 * 保证同一用户对同一题目只能收藏一次。</p>
 */
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(name = "uq_favorites_user_problem", columnNames = {"user_id", "problem_id"}))
public class Favorite extends BaseEntity {
    /** 收藏用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    /** 被收藏的题目 ID。 */
    @Column(name = "problem_id", nullable = false)
    private Integer problemId;

    protected Favorite() {}
    /** 构建收藏关系。 */
    public static Favorite of(User user, Integer problemId) { Favorite favorite = new Favorite(); favorite.user = user; favorite.problemId = problemId; return favorite; }
    /** 返回题目 ID。 */
    public Integer getProblemId() { return problemId; }
}
