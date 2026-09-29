package com.xauat.oj.core.announcement.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(nullable = false, length = 50)
    private String category = "系统公告";
    @Column(nullable = false, length = 20)
    private String permission = "member";
    @Column(name = "created_by", length = 50)
    private String createdBy;
    @Column(name = "is_published", nullable = false)
    private boolean published = true;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected Announcement() {}
    public Integer getId() { return super.getId(); }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public String getPermission() { return permission; }
    public boolean isPublished() { return published; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public static Announcement create(String title, String content, String category, String permission, String createdBy, boolean published) { Announcement item = new Announcement(); item.title = title; item.content = content; item.category = category; item.permission = permission; item.createdBy = createdBy; item.published = published; item.publishedAt = published ? LocalDateTime.now() : null; return item; }
    public void update(String title, String content, String category, String permission, boolean published) { this.title = title; this.content = content; this.category = category; this.permission = permission; this.published = published; if (published && publishedAt == null) publishedAt = LocalDateTime.now(); }
}
