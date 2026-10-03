package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ContestJudgeOutboxRepository extends JpaRepository<ContestJudgeOutbox, Integer> {
    /** 原生 SQL 自带 FOR UPDATE SKIP LOCKED，不能再叠加 @Lock。 */
    @Query(value = "select * from contest_judge_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    List<ContestJudgeOutbox> claimPending();

    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.submission.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId") Integer problemId);

    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.submission.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);

    long countByState(String state);

    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.state = 'DISPATCHED' and o.updatedAt < :cutoff "
            + "and o.submission.rejudgeOf is null and o.submission.status in :statuses")
    int deleteDispatchedFinalBefore(@Param("cutoff") java.time.LocalDateTime cutoff, @Param("statuses") java.util.Collection<String> statuses);

    @Query("select min(o.createdAt) from ContestJudgeOutbox o where o.state = 'PENDING'")
    java.time.LocalDateTime oldestPending();
}
