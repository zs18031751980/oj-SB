package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

public interface ContestSubmissionRepository extends JpaRepository<ContestSubmission, Integer> {
    List<ContestSubmission> findByContestIdOrderByIdDesc(Integer contestId);
    List<ContestSubmission> findByContestIdAndUserIdOrderByIdDesc(Integer contestId, Integer userId);
    Optional<ContestSubmission> findByContestIdAndUserIdAndIdempotencyKey(Integer contestId, Integer userId, String idempotencyKey);
    Optional<ContestSubmission> findByJobId(String jobId);
    List<ContestSubmission> findByContestIdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    List<ContestSubmission> findByContestIdAndRejudgeOfIsNullAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    long countByContestIdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    @Modifying
    @Transactional
    @Query("update ContestSubmission s set s.status = :status, s.verdict = :status, s.cpuTime = :cpuTime, s.memory = :memory, s.passed = :passed, s.total = :total, s.testcaseResults = :payload, s.finishedAt = CURRENT_TIMESTAMP where s.id = :id and s.attemptId = :attempt and s.status = 'Judging'")
    int updateResultIfCurrentAttempt(@Param("id") Integer id, @Param("attempt") int attempt, @Param("status") String status, @Param("cpuTime") Integer cpuTime, @Param("memory") Long memory, @Param("passed") int passed, @Param("total") int total, @Param("payload") String payload);
}
