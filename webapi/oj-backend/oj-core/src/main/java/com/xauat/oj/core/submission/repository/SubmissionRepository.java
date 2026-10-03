package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Integer> {
    List<Submission> findByUser_IdOrderByIdDesc(Integer userId);
    Optional<Submission> findByJobId(String jobId);
    Optional<Submission> findByUser_IdAndIdempotencyKey(Integer userId, String idempotencyKey);
    long countByUser_IdAndStatusIn(Integer userId, Collection<String> statuses);
}
