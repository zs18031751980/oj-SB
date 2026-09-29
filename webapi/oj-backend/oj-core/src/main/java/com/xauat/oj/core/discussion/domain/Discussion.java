package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "discussions")
public class Discussion extends BaseEntity {
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(length = 50) private String category = "全部";
    @Column(length = 500) private String tags;
    @Column(name = "reply_count") private int replyCount;
    @Column(name = "like_count") private int likeCount;
    @Column(name = "view_count") private int viewCount;
    @Column(name = "is_pinned") private boolean pinned;
    @Column(name = "is_closed") private boolean closed;
    protected Discussion() {}
    public static Discussion create(User author, String title, String content, String category, String tags) { Discussion item = new Discussion(); item.author = author; item.title = title; item.content = content; item.category = category; item.tags = tags; return item; }
    public Integer getId() { return super.getId(); }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public String getTags() { return tags; }
    public int getReplyCount() { return replyCount; }
    public int getLikeCount() { return likeCount; }
    public int getViewCount() { return viewCount; }
    public String getAuthorName() { return author == null ? "匿名" : author.getUsername(); }
    public Integer getAuthorId() { return author == null ? null : author.getId(); }
    public boolean isPinned() { return pinned; }
    public boolean isClosed() { return closed; }
    public void view() { viewCount++; }
    public void replyAdded() { replyCount++; }
    public void likeAdded() { likeCount++; }
    public void likeRemoved() { if (likeCount > 0) likeCount--; }
}
