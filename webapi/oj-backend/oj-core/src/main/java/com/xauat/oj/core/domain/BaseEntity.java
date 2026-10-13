package com.xauat.oj.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 领域实体公共基类。
 *
 * <p>所有持久化实体统一继承本类，提供自增主键与创建/更新时间审计字段，
 * 由 JPA 审计监听器（{@link AuditingEntityListener}）及生命周期回调自动维护。</p>
 *
 * <p>本类不映射为独立表（{@code @MappedSuperclass}），其字段直接并入各子类对应的数据库表。</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    /** 主键 ID，数据库自增生成。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 记录创建时间，由 Spring Data 审计在插入时自动填充。 */
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 记录最后更新时间，由 Spring Data 审计在每次更新时自动刷新。 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 持久化前回调：兜底填充创建与更新时间（审计未生效时保证非空）。 */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (updatedAt == null)
            updatedAt = now;
    }

    /** 更新前回调：将最后更新时间刷新为当前时间。 */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
