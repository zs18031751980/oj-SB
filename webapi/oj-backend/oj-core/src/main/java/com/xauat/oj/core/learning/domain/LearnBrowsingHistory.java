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

@Entity
@Table(name = "learn_browsing_history")
public class LearnBrowsingHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;
    @Column(name = "browsed_at", nullable = false)
    private LocalDateTime browsedAt;

    protected LearnBrowsingHistory() {}
    public static LearnBrowsingHistory of(User user, String resourceId) { LearnBrowsingHistory history = new LearnBrowsingHistory(); history.user = user; history.resourceId = resourceId; history.browsedAt = LocalDateTime.now(); return history; }
    public String getResourceId() { return resourceId; }
    public LocalDateTime getBrowsedAt() { return browsedAt; }
}
