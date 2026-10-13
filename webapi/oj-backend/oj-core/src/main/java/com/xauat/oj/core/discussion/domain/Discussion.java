package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 讨论帖实体，对应数据库表 {@code discussions}。
 *
 * <p>社区论坛的主帖，聚合回复数、点赞数、浏览数与置顶/关闭标记。</p>
 */
@Entity
@Table(name = "discussions")
public class Discussion extends BaseEntity {
    /** 帖子标题，最长 200 字符。 */
    @Column(nullable = false, length = 200)
    private String title;
    /** 帖子正文。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    /** 发帖用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;
    /** 分类，默认“全部”。 */
    @Column(length = 50)
    private String category = "全部";
    /** 标签，多个以分隔符连接，最长 500 字符。 */
    @Column(length = 500)
    private String tags;
    /** 回复数（冗余计数）。 */
    @Column(name = "reply_count")
    private int replyCount;
    /** 点赞数（冗余计数）。 */
    @Column(name = "like_count")
    private int likeCount;
    /** 浏览数（冗余计数）。 */
    @Column(name = "view_count")
    private int viewCount;
    /** 是否置顶。 */
    @Column(name = "is_pinned")
    private boolean pinned;
    /** 是否关闭（禁止继续回复）。 */
    @Column(name = "is_closed")
    private boolean closed;
    protected Discussion() {
    }
    /** 创建讨论帖。 */
    public static Discussion create(User author, String title, String content, String category, String tags) {
        Discussion item = new Discussion();
        item.author = author;
        item.title = title;
        item.content = content;
        item.category = category;
        item.tags = tags;
        return item;
    }
    /** 返回帖子 ID。 */
    public Integer getId() {
        return super.getId();
    }
    /** 返回标题。 */
    public String getTitle() {
        return title;
    }
    /** 返回正文。 */
    public String getContent() {
        return content;
    }
    /** 返回分类。 */
    public String getCategory() {
        return category;
    }
    /** 返回标签。 */
    public String getTags() {
        return tags;
    }
    /** 返回回复数。 */
    public int getReplyCount() {
        return replyCount;
    }
    /** 返回点赞数。 */
    public int getLikeCount() {
        return likeCount;
    }
    /** 返回浏览数。 */
    public int getViewCount() {
        return viewCount;
    }
    /** 返回作者用户名，作者缺失时显示“匿名”。 */
    public String getAuthorName() {
        return author == null ? "匿名" : author.getUsername();
    }
    /** 返回作者 ID。 */
    public Integer getAuthorId() {
        return author == null ? null : author.getId();
    }
    /** 是否置顶。 */
    public boolean isPinned() {
        return pinned;
    }
    /** 是否关闭。 */
    public boolean isClosed() {
        return closed;
    }
    /** 浏览数加一。 */
    public void view() {
        viewCount++;
    }
    /** 回复数加一。 */
    public void replyAdded() {
        replyCount++;
    }
    /** 点赞数加一。 */
    public void likeAdded() {
        likeCount++;
    }
    /** 取消点赞，点赞数减一（不小于 0）。 */
    public void likeRemoved() {
        if (likeCount > 0)
            likeCount--;
    }
}
