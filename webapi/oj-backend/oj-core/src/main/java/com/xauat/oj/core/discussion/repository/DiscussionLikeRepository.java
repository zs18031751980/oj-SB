package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.DiscussionLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionLikeRepository extends JpaRepository<DiscussionLike, Integer> {
    boolean existsByDiscussionIdAndUserId(Integer discussionId, Integer userId);
    void deleteByDiscussionIdAndUserId(Integer discussionId, Integer userId);
}
