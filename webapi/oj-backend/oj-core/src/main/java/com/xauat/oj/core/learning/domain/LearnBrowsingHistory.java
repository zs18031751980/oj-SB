package com.xauat.oj.core.learning.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 学习中心浏览记录实体，对应数据库表 {@code learn_browsing_history}。
 *
 * <p>记录用户浏览学习资源的行为，用于展示学习历史与推荐。</p>
 */
@Entity
@Table(name = "learn_browsing_history")
public class LearnBrowsingHistory extends BaseEntity {
    /** 浏览用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    /** 学习资源标识。 */
    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;
    /** 浏览发生时间。 */
    @Column(name = "browsed_at", nullable = false)
    private LocalDateTime browsedAt;

    protected LearnBrowsingHistory() {}
    /** 构建一条浏览记录并记录当前时间。 */
    public static LearnBrowsingHistory of(User user, String resourceId) { LearnBrowsingHistory history = new LearnBrowsingHistory(); history.user = user; history.resourceId = resourceId; history.browsedAt = LocalDateTime.now(); return history; }
    /** 返回资源标识。 */
    public String getResourceId() { return resourceId; }
    /** 返回浏览时间。 */
    public LocalDateTime getBrowsedAt() { return browsedAt; }
}
