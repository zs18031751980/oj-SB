package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测死信仓储。
 * <p>管理评测失败进入死信队列的实体 {@link JudgeDeadLetter}，支持人工排查与定期清理。</p>
 */
public interface JudgeDeadLetterRepository extends JpaRepository<JudgeDeadLetter, Integer> {
    /**
     * 查询最新的 100 条死信记录，按主键倒序。
     *
     * @return 死信记录列表，最近产生的在前，无数据时返回空列表
     */
    List<JudgeDeadLetter> findTop100ByOrderByIdDesc();

    /**
     * 删除指定时间之前已解决（{@code resolvedAt} 非空）的死信记录。
     *
     * @param cutoff 截止时间，解决时间早于该时间的记录会被删除
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from JudgeDeadLetter d where d.resolvedAt is not null and d.resolvedAt < :cutoff")
    int deleteResolvedBefore(@Param("cutoff") LocalDateTime cutoff);
}
