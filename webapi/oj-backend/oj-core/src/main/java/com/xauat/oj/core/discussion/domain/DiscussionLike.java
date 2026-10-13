package com.xauat.oj.core.discussion.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 讨论帖点赞实体，对应数据库表 {@code discussion_likes}。
 *
 * <p>记录用户对讨论帖的点赞关系；通过 {@code (discussion_id, user_id)}
 * 唯一约束保证同一用户对同一帖子只能点赞一次。</p>
 */
@Entity
@Table(name = "discussion_likes", uniqueConstraints = @UniqueConstraint(name = "uq_discussion_like", columnNames = {"discussion_id", "user_id"}))
public class DiscussionLike extends BaseEntity {
    /** 被点赞的讨论帖。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "discussion_id") private Discussion discussion;
    /** 点赞用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    protected DiscussionLike() {}
    /** 构建点赞关系。 */
    public static DiscussionLike of(Discussion discussion, User user) { DiscussionLike item = new DiscussionLike(); item.discussion = discussion; item.user = user; return item; }
}
