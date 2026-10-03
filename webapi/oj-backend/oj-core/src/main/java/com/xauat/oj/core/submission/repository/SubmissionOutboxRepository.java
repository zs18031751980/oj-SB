package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SubmissionOutboxRepository extends JpaRepository<SubmissionOutbox, Integer> {
    /** 原生 SQL 自带 FOR UPDATE SKIP LOCKED，不能再叠加 @Lock。 */
    @Query(value = "select * from submission_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    java.util.List<SubmissionOutbox> claimPending();

    long countByState(String state);

    @Modifying
    @Query("delete from SubmissionOutbox o where o.state = 'DISPATCHED' and o.updatedAt < :cutoff")
    int deleteDispatchedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("select min(o.createdAt) from SubmissionOutbox o where o.state = 'PENDING'")
    LocalDateTime oldestPending();
}
