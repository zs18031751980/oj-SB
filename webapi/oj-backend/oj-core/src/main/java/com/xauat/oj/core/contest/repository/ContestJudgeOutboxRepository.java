package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;

public interface ContestJudgeOutboxRepository extends JpaRepository<ContestJudgeOutbox, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select * from contest_judge_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    List<ContestJudgeOutbox> claimPending();
}
