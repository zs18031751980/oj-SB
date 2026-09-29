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

@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(name = "uq_favorites_user_problem", columnNames = {"user_id", "problem_id"}))
public class Favorite extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "problem_id", nullable = false)
    private Integer problemId;

    protected Favorite() {}
    public static Favorite of(User user, Integer problemId) { Favorite favorite = new Favorite(); favorite.user = user; favorite.problemId = problemId; return favorite; }
    public Integer getProblemId() { return problemId; }
}
