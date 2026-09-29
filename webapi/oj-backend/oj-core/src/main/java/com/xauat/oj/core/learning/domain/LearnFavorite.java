package com.xauat.oj.core.learning.domain;

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
@Table(name = "learn_favorites", uniqueConstraints = @UniqueConstraint(name = "uq_learn_favorites_user_resource", columnNames = {"user_id", "resource_id"}))
public class LearnFavorite extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    protected LearnFavorite() {}
    public static LearnFavorite of(User user, String resourceId) { LearnFavorite favorite = new LearnFavorite(); favorite.user = user; favorite.resourceId = resourceId; return favorite; }
    public String getResourceId() { return resourceId; }
}
