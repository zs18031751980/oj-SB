package com.xauat.oj.core.discussion.repository;
import com.xauat.oj.core.discussion.domain.DiscussionReplyLike;
import org.springframework.data.jpa.repository.JpaRepository;
/**
 * 讨论回复点赞仓储。
 * <p>管理讨论回复点赞实体 {@link DiscussionReplyLike}，用于记录用户对回复的点赞关系。</p>
 */
public interface DiscussionReplyLikeRepository extends JpaRepository<DiscussionReplyLike, Integer> {
    /**
     * 判断指定用户是否已点赞某条回复。
     *
     * @param replyId 回复 ID
     * @param userId 用户 ID
     * @return 已点赞返回 {@code true}，否则返回 {@code false}
     */
    boolean existsByReply_IdAndUser_Id(Integer replyId, Integer userId);

    /**
     * 取消指定用户对某条回复的点赞（物理删除）。
     *
     * @param replyId 回复 ID
     * @param userId 用户 ID
     */
    void deleteByReply_IdAndUser_Id(Integer replyId, Integer userId);
}
