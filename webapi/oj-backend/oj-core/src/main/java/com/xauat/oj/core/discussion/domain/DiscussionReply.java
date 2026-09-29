package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "discussion_replies")
public class DiscussionReply extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "discussion_id") private Discussion discussion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "like_count") private int likeCount;
    protected DiscussionReply() {}
    public static DiscussionReply create(Discussion discussion, User author, String content) { DiscussionReply item = new DiscussionReply(); item.discussion = discussion; item.author = author; item.content = content; return item; }
    public Integer getId() { return super.getId(); }
    public String getContent() { return content; }
    public String getAuthorName() { return author == null ? "匿名" : author.getUsername(); }
    public Integer getAuthorId() { return author == null ? null : author.getId(); }
    public int getLikeCount() { return likeCount; }
    public void likeAdded() { likeCount++; }
    public void likeRemoved() { if (likeCount > 0) likeCount--; }
}
