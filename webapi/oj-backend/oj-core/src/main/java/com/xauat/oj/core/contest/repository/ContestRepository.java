package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.Contest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Integer> {
    /** 关键写路径串行化：发布/报名/提交/复判/结算/改规则等都先锁 Contest 行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contest c where c.id = :id")
    Optional<Contest> findForUpdateById(@Param("id") Integer id);
}
