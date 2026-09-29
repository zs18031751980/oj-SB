package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReferenceValidationJobRepository extends JpaRepository<ReferenceValidationJob, String> {
    List<ReferenceValidationJob> findByProblemIdOrderByCreatedAtDesc(Integer problemId);
}
