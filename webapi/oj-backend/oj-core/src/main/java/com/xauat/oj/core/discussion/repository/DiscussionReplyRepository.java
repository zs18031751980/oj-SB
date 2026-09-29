package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.DiscussionReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiscussionReplyRepository extends JpaRepository<DiscussionReply, Integer> {
    List<DiscussionReply> findByDiscussionIdOrderByCreatedAtAscIdAsc(Integer discussionId);
}
