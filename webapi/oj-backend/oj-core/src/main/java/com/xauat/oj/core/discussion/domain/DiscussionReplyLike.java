package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "discussion_reply_likes", uniqueConstraints = @UniqueConstraint(name = "uq_discussion_reply_like", columnNames = {"reply_id", "user_id"}))
public class DiscussionReplyLike extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reply_id") private DiscussionReply reply;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    protected DiscussionReplyLike() {}
    public static DiscussionReplyLike of(DiscussionReply reply, User user) { DiscussionReplyLike item = new DiscussionReplyLike(); item.reply = reply; item.user = user; return item; }
}
