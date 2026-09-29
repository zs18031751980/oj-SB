package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "discussion_likes", uniqueConstraints = @UniqueConstraint(name = "uq_discussion_like", columnNames = {"discussion_id", "user_id"}))
public class DiscussionLike extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "discussion_id") private Discussion discussion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    protected DiscussionLike() {}
    public static DiscussionLike of(Discussion discussion, User user) { DiscussionLike item = new DiscussionLike(); item.discussion = discussion; item.user = user; return item; }
}
