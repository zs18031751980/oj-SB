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
    List<ContestSubmission> findByContest_IdOrderByIdDesc(Integer contestId);
    List<ContestSubmission> findByContest_IdAndUser_IdOrderByIdDesc(Integer contestId, Integer userId);
    Optional<ContestSubmission> findByContest_IdAndUser_IdAndIdempotencyKey(Integer contestId, Integer userId, String idempotencyKey);
    Optional<ContestSubmission> findByJobId(String jobId);
    List<ContestSubmission> findByContest_IdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    List<ContestSubmission> findByContest_IdAndRejudgeOfIsNullAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    long countByContest_IdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);
    long countByUser_IdAndContestEligibleFalseAndRejudgeOfIsNullAndStatusNotIn(Integer userId, java.util.Collection<String> statuses);
    java.util.List<ContestSubmission> findByIdempotencyKeyStartingWith(String prefix);

    @Modifying
    @Query("delete from ContestSubmission s where s.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId") Integer problemId);

    @Modifying
    @Query("delete from ContestSubmission s where s.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
    @Modifying
    @Transactional
    @Query("update ContestSubmission s set s.status = :status, s.verdict = :status, s.cpuTime = :cpuTime, s.memory = :memory, s.passed = :passed, s.total = :total, s.testcaseResults = :payload, s.finishedAt = CURRENT_TIMESTAMP where s.id = :id and s.attemptId = :attempt and s.status = 'Judging'")
    int updateResultIfCurrentAttempt(@Param("id") Integer id, @Param("attempt") int attempt, @Param("status") String status, @Param("cpuTime") Integer cpuTime, @Param("memory") Long memory, @Param("passed") int passed, @Param("total") int total, @Param("payload") String payload);

    @Modifying
    @Transactional
    @Query("update ContestSubmission s set s.compileStartedAt = :compileStart, s.compileFinishedAt = :compileEnd, "
            + "s.executionStartedAt = :executionStart, s.executionFinishedAt = :executionEnd, s.checkedAt = :executionEnd, "
            + "s.outputSize = :outputSize, s.exitCode = :exitCode, s.signal = :signal, s.packageDigest = :packageDigest "
            + "where s.id = :id and s.attemptId = :attempt")
    int updateMetricsIfCurrentAttempt(@Param("id") Integer id, @Param("attempt") int attempt,
                                      @Param("compileStart") java.time.LocalDateTime compileStart, @Param("compileEnd") java.time.LocalDateTime compileEnd,
                                      @Param("executionStart") java.time.LocalDateTime executionStart, @Param("executionEnd") java.time.LocalDateTime executionEnd,
                                      @Param("outputSize") Integer outputSize, @Param("exitCode") Integer exitCode,
                                      @Param("signal") Integer signal, @Param("packageDigest") String packageDigest);
}
