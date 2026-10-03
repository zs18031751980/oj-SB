package com.xauat.oj.core.discussion.repository;
import com.xauat.oj.core.discussion.domain.DiscussionReplyLike;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DiscussionReplyLikeRepository extends JpaRepository<DiscussionReplyLike,Integer> {
    boolean existsByReply_IdAndUser_Id(Integer replyId, Integer userId);
    void deleteByReply_IdAndUser_Id(Integer replyId, Integer userId);
}
