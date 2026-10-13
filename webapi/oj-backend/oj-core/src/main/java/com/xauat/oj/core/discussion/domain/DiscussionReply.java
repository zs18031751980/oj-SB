package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 讨论帖回复实体，对应数据库表 {@code discussion_replies}。
 *
 * <p>记录某条讨论帖下的回复内容及其点赞数。</p>
 */
@Entity
@Table(name = "discussion_replies")
public class DiscussionReply extends BaseEntity {
    /** 所属讨论帖。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discussion_id")
    private Discussion discussion;
    /** 回复作者。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;
    /** 回复正文。 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    /** 点赞数（冗余计数）。 */
    @Column(name = "like_count")
    private int likeCount;
    protected DiscussionReply() {
    }
    /** 创建回复。 */
    public static DiscussionReply create(Discussion discussion, User author, String content) {
        DiscussionReply item = new DiscussionReply();
        item.discussion = discussion;
        item.author = author;
        item.content = content;
        return item;
    }
    /** 返回回复 ID。 */
    public Integer getId() {
        return super.getId();
    }
    /** 返回回复正文。 */
    public String getContent() {
        return content;
    }
    /** 返回作者用户名，作者缺失时显示“匿名”。 */
    public String getAuthorName() {
        return author == null ? "匿名" : author.getUsername();
    }
    /** 返回作者 ID。 */
    public Integer getAuthorId() {
        return author == null ? null : author.getId();
    }
    /** 返回点赞数。 */
    public int getLikeCount() {
        return likeCount;
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
