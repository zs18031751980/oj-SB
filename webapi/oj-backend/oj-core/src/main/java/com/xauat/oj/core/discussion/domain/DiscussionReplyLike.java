package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 讨论回复点赞实体，对应数据库表 {@code discussion_reply_likes}。
 *
 * <p>记录用户对回复的点赞关系；通过 {@code (reply_id, user_id)}
 * 唯一约束保证同一用户对同一回复只能点赞一次。</p>
 */
@Entity
@Table(name = "discussion_reply_likes", uniqueConstraints = @UniqueConstraint(name = "uq_discussion_reply_like", columnNames = {"reply_id", "user_id"}))
public class DiscussionReplyLike extends BaseEntity {
    /** 被点赞的回复。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reply_id") private DiscussionReply reply;
    /** 点赞用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    protected DiscussionReplyLike() {}
    /** 构建回复点赞关系。 */
    public static DiscussionReplyLike of(DiscussionReply reply, User user) { DiscussionReplyLike item = new DiscussionReplyLike(); item.reply = reply; item.user = user; return item; }
}
