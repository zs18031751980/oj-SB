package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Integer> {
    List<Submission> findByUserIdOrderByIdDesc(Integer userId);
    Optional<Submission> findByJobId(String jobId);
    Optional<Submission> findByUserIdAndIdempotencyKey(Integer userId, String idempotencyKey);
}
