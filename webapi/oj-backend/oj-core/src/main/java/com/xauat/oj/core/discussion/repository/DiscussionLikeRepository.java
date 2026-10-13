package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.DiscussionLike;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 讨论帖点赞仓储。
 * <p>管理讨论帖点赞实体 {@link DiscussionLike}，用于记录用户对讨论帖的点赞关系。</p>
 */
public interface DiscussionLikeRepository extends JpaRepository<DiscussionLike, Integer> {
    /**
     * 判断指定用户是否已点赞某讨论帖。
     *
     * @param discussionId 讨论帖 ID
     * @param userId 用户 ID
     * @return 已点赞返回 {@code true}，否则返回 {@code false}
     */
    boolean existsByDiscussion_IdAndUser_Id(Integer discussionId, Integer userId);

    /**
     * 取消指定用户对某讨论帖的点赞（物理删除）。
     *
     * @param discussionId 讨论帖 ID
     * @param userId 用户 ID
     */
    void deleteByDiscussion_IdAndUser_Id(Integer discussionId, Integer userId);
}
