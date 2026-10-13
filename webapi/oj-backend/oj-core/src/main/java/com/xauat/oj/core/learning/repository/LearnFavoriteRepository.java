package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 学习资料收藏仓储。
 * <p>管理学习资料收藏实体 {@link LearnFavorite}，用于记录用户对学习资源的收藏关系。</p>
 */
public interface LearnFavoriteRepository extends JpaRepository<LearnFavorite, Integer> {
    /**
     * 查询指定用户的全部学习收藏，按主键倒序（最新收藏在前）。
     *
     * @param userId 用户 ID
     * @return 收藏列表，无数据时返回空列表
     */
    List<LearnFavorite> findByUser_IdOrderByIdDesc(Integer userId);

    /**
     * 判断指定用户是否已收藏某学习资源。
     *
     * @param userId 用户 ID
     * @param resourceId 学习资源 ID
     * @return 已收藏返回 {@code true}，否则返回 {@code false}
     */
    boolean existsByUser_IdAndResourceId(Integer userId, String resourceId);

    /**
     * 取消指定用户对某学习资源的收藏（物理删除）。
     *
     * @param userId 用户 ID
     * @param resourceId 学习资源 ID
     */
    void deleteByUser_IdAndResourceId(Integer userId, String resourceId);
}
