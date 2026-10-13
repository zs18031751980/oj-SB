package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.DiscussionReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 讨论回复仓储。
 * <p>管理讨论帖回复实体 {@link DiscussionReply} 的持久化与查询。</p>
 */
public interface DiscussionReplyRepository extends JpaRepository<DiscussionReply, Integer> {
    /**
     * 查询某讨论帖下的全部回复，按创建时间正序、主键正序排列（楼层顺序）。
     *
     * @param discussionId 讨论帖 ID
     * @return 回复列表，按发布先后排列，无数据时返回空列表
     */
    List<DiscussionReply> findByDiscussion_IdOrderByCreatedAtAscIdAsc(Integer discussionId);
}
