package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.JudgeDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JudgeDeadLetterRepository extends JpaRepository<JudgeDeadLetter, Integer> {
    List<JudgeDeadLetter> findTop100ByOrderByIdDesc();

    @Modifying
    @Query("delete from JudgeDeadLetter d where d.resolvedAt is not null and d.resolvedAt < :cutoff")
    int deleteResolvedBefore(@Param("cutoff") LocalDateTime cutoff);
}
