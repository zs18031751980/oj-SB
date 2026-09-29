package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface SubmissionOutboxRepository extends JpaRepository<SubmissionOutbox, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select * from submission_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    java.util.List<SubmissionOutbox> claimPending();
}
