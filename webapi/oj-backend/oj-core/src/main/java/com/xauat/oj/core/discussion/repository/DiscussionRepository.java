package com.xauat.oj.core.discussion.repository;

import com.xauat.oj.core.discussion.domain.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 讨论帖仓储。
 * <p>管理讨论帖实体 {@link Discussion} 的持久化与列表查询。</p>
 */
public interface DiscussionRepository extends JpaRepository<Discussion, Integer> {
    /**
     * 查询全部讨论帖，按创建时间倒序、主键倒序排列。
     *
     * @return 讨论帖列表，最新发布在前，无数据时返回空列表
     */
    List<Discussion> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 按分类查询讨论帖，按创建时间倒序、主键倒序排列。
     *
     * @param category 讨论分类
     * @return 该分类下的讨论帖列表，最新发布在前，无数据时返回空列表
     */
    List<Discussion> findByCategoryOrderByCreatedAtDescIdDesc(String category);
}
