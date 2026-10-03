package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.DiscussionLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionLikeRepository extends JpaRepository<DiscussionLike, Integer> {
    boolean existsByDiscussion_IdAndUser_Id(Integer discussionId, Integer userId);
    void deleteByDiscussion_IdAndUser_Id(Integer discussionId, Integer userId);
}
