package com.xauat.oj.core.discussion.repository;
import com.xauat.oj.core.discussion.domain.DiscussionReplyLike;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DiscussionReplyLikeRepository extends JpaRepository<DiscussionReplyLike,Integer> {
    boolean existsByReplyIdAndUserId(Integer replyId, Integer userId);
    void deleteByReplyIdAndUserId(Integer replyId, Integer userId);
}
