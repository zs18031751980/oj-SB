package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReferenceValidationJobRepository extends JpaRepository<ReferenceValidationJob, String> {
    List<ReferenceValidationJob> findByProblem_IdOrderByCreatedAtDesc(Integer problemId);
    List<ReferenceValidationJob> findTop20ByStateOrderByCreatedAtAsc(String state);
    long countByState(String state);

    @org.springframework.data.jpa.repository.Query("select min(j.createdAt) from ReferenceValidationJob j where j.state = 'PENDING'")
    java.time.LocalDateTime oldestPending();
}
