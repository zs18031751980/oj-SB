package com.xauat.oj.core.learning.repository;

import com.xauat.oj.core.learning.domain.LearnBrowsingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习浏览历史仓储。
 * <p>管理学习资源浏览历史实体 {@link LearnBrowsingHistory}，用于记录并清理用户的浏览记录。</p>
 */
public interface LearnBrowsingHistoryRepository extends JpaRepository<LearnBrowsingHistory, Integer> {
    /**
     * 查询指定用户的浏览历史，按浏览时间 {@code browsedAt} 倒序（最近浏览在前）。
     *
     * @param userId 用户 ID
     * @return 浏览历史列表，无数据时返回空列表
     */
    List<LearnBrowsingHistory> findByUser_IdOrderByBrowsedAtDesc(Integer userId);

    /**
     * 删除指定用户的全部浏览历史。
     *
     * @param userId 用户 ID
     */
    void deleteByUser_Id(Integer userId);

    /**
     * 批量删除浏览时间早于指定截止时间的历史记录。
     *
     * <p>用于定时清理过期浏览历史。</p>
     *
     * @param cutoff 截止时间，早于该时间的记录会被删除
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from LearnBrowsingHistory h where h.browsedAt < :cutoff")
    int deleteBefore(@Param("cutoff")
    LocalDateTime cutoff);
}
