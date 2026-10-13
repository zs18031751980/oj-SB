package com.xauat.oj.core.announcement.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 系统公告实体，对应数据库表 {@code announcements}。
 *
 * <p>用于平台向用户发布通知/公告，支持分类、可见权限与发布开关；
 * 仅在 {@code published=true} 时对用户可见，并记录首次发布时间。</p>
 */
@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {
    /** 公告标题，最长 200 字符。 */
    @Column(nullable = false, length = 200)
    private String title;
    /** 公告正文，使用 text 类型存储，支持较长内容。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    /** 公告分类，默认“系统公告”。 */
    @Column(nullable = false, length = 50)
    private String category = "系统公告";
    /** 可见权限标识，如 member（普通成员可见）等；默认 member。 */
    @Column(nullable = false, length = 20)
    private String permission = "member";
    /** 创建人用户名/标识。 */
    @Column(name = "created_by", length = 50)
    private String createdBy;
    /** 是否已发布；false 时为草稿，不对用户展示。 */
    @Column(name = "is_published", nullable = false)
    private boolean published = true;
    /** 首次发布时间；未发布时为 null。 */
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
    /** 创建公告；若直接发布则同时写入发布时间。 */
    public static Announcement create(String title, String content, String category, String permission, String createdBy, boolean published) { Announcement item = new Announcement(); item.title = title; item.content = content; item.category = category; item.permission = permission; item.createdBy = createdBy; item.published = published; item.publishedAt = published ? LocalDateTime.now() : null; return item; }
    /** 更新公告内容；首次发布时补充发布时间（已有发布时间则保留）。 */
    public void update(String title, String content, String category, String permission, boolean published) { this.title = title; this.content = content; this.category = category; this.permission = permission; this.published = published; if (published && publishedAt == null) publishedAt = LocalDateTime.now(); }
}
