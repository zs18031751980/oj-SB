package com.xauat.oj.core.favorite.repository;

import com.xauat.oj.core.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 题目收藏仓储。
 * <p>管理题目收藏实体 {@link Favorite}，用于记录用户对题目的收藏关系。</p>
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {
    /**
     * 查询指定用户的全部题目收藏，按主键倒序（最新收藏在前）。
     *
     * @param userId 用户 ID
     * @return 收藏列表，无数据时返回空列表
     */
    List<Favorite> findByUser_IdOrderByIdDesc(Integer userId);

    /**
     * 判断指定用户是否已收藏某题目。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @return 已收藏返回 {@code true}，否则返回 {@code false}
     */
    boolean existsByUser_IdAndProblemId(Integer userId, Integer problemId);

    /**
     * 取消指定用户对某题目的收藏（物理删除）。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     */
    void deleteByUser_IdAndProblemId(Integer userId, Integer problemId);
}
