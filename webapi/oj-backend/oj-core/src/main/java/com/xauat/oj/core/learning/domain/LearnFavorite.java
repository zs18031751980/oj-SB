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

/**
 * 学习中心收藏实体，对应数据库表 {@code learn_favorites}。
 *
 * <p>记录用户收藏的学习资源；通过 {@code (user_id, resource_id)} 唯一约束
 * 保证同一用户对同一资源只能收藏一次。</p>
 */
@Entity
@Table(name = "learn_favorites", uniqueConstraints =
        @UniqueConstraint(name = "uq_learn_favorites_user_resource", columnNames = {
        "user_id", "resource_id"}))
public class LearnFavorite extends BaseEntity {
    /** 收藏用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    /** 学习资源标识。 */
    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    protected LearnFavorite() {
    }
    /** 构建收藏关系。 */
    public static LearnFavorite of(User user, String resourceId) {
        LearnFavorite favorite = new LearnFavorite();
        favorite.user = user;
        favorite.resourceId = resourceId;
        return favorite;
    }
    /** 返回资源标识。 */
    public String getResourceId() {
        return resourceId;
    }
}
