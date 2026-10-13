package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.Contest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 赛事仓储。
 * <p>管理赛事实体 {@link Contest} 的持久化，并提供关键写路径所需的加锁查询。</p>
 */
public interface ContestRepository extends JpaRepository<Contest, Integer> {
    /**
     * 关键写路径串行化：发布/报名/提交/复判/结算/改规则等都先锁 Contest 行。
     *
     * <p>以悲观写锁（{@code PESSIMISTIC_WRITE}）按赛事 ID 查询，调用方需在事务中持有锁直至写操作完成。</p>
     *
     * @param id 赛事 ID
     * @return 加锁查询到的赛事，不存在时返回空 {@code Optional}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contest c where c.id = :id")
    Optional<Contest> findForUpdateById(@Param("id")
    Integer id);
}
