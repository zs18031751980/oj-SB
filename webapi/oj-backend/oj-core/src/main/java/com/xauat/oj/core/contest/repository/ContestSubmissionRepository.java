package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

/**
 * 赛事提交仓储。
 * <p>管理赛事提交记录实体 {@link ContestSubmission} 的持久化、查询、批量清理与评测结果更新。</p>
 */
public interface ContestSubmissionRepository extends JpaRepository<ContestSubmission, Integer> {
    /**
     * 查询某赛事的全部提交，按主键倒序（最新提交在前）。
     *
     * @param contestId 赛事 ID
     * @return 提交列表，无数据时返回空列表
     */
    List<ContestSubmission> findByContest_IdOrderByIdDesc(Integer contestId);

    /**
     * 查询某赛事中指定用户的全部提交，按主键倒序（最新提交在前）。
     *
     * @param contestId 赛事 ID
     * @param userId 用户 ID
     * @return 提交列表，无数据时返回空列表
     */
    List<ContestSubmission> findByContest_IdAndUser_IdOrderByIdDesc(Integer contestId, Integer userId);

    /**
     * 根据赛事、用户与幂等键查询提交，用于提交接口的幂等去重。
     *
     * @param contestId 赛事 ID
     * @param userId 用户 ID
     * @param idempotencyKey 幂等键
     * @return 匹配的提交，未找到时返回空 {@code Optional}
     */
    Optional<ContestSubmission> findByContest_IdAndUser_IdAndIdempotencyKey(Integer contestId, Integer userId,
            String idempotencyKey);

    /**
     * 根据评测任务 ID（{@code jobId}）查询提交。
     *
     * @param jobId 评测任务 ID
     * @return 匹配的提交，未找到时返回空 {@code Optional}
     */
    Optional<ContestSubmission> findByJobId(String jobId);

    /**
     * 查询某赛事中处于指定状态集合的提交。
     *
     * @param contestId 赛事 ID
     * @param statuses 状态集合
     * @return 满足条件的提交列表，无数据时返回空列表
     */
    List<ContestSubmission> findByContest_IdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);

    /**
     * 查询某赛事中非重判（{@code rejudgeOf} 为空）且处于指定状态集合的提交。
     *
     * @param contestId 赛事 ID
     * @param statuses 状态集合
     * @return 满足条件的提交列表，无数据时返回空列表
     */
    List<ContestSubmission> findByContest_IdAndRejudgeOfIsNullAndStatusIn(Integer contestId,
            java.util.Collection<String> statuses);

    /**
     * 统计某赛事中处于指定状态集合的提交数量。
     *
     * @param contestId 赛事 ID
     * @param statuses 状态集合
     * @return 满足条件的提交条数
     */
    long countByContest_IdAndStatusIn(Integer contestId, java.util.Collection<String> statuses);

    /**
     * 统计某用户「非赛事有效」（{@code contestEligible = false}）且非重判、状态不在给定集合中的提交数量。
     *
     * @param userId 用户 ID
     * @param statuses 需排除的状态集合
     * @return 满足条件的提交条数
     */
    long countByUser_IdAndContestEligibleFalseAndRejudgeOfIsNullAndStatusNotIn(Integer userId,
            java.util.Collection<String> statuses);

    /**
     * 按幂等键前缀查询提交。
     *
     * @param prefix 幂等键前缀
     * @return 匹配的提交列表，无数据时返回空列表
     */
    java.util.List<ContestSubmission> findByIdempotencyKeyStartingWith(String prefix);

    /**
     * 删除指定赛事题目关联下的全部提交。
     *
     * @param problemId 赛事题目关联 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestSubmission s where s.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId")
    Integer problemId);

    /**
     * 删除指定赛事下的全部提交。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestSubmission s where s.contest.id = :contestId")
    int deleteByContestId(@Param("contestId")
    Integer contestId);

    /**
     * 仅当尝试次数（{@code attemptId}）仍为当前尝试且状态为 {@code Judging} 时，更新提交的最终评测结果。
     *
     * <p>用于防止过期或重复的评测回调覆盖更新后的结果；更新字段包括状态、判决、CPU 时间、内存、
     * 通过用例数、总用例数、测试点结果及完成时间。</p>
     *
     * @return 更新影响的行数：{@code 0} 表示尝试已过期或被覆盖，未发生更新
     */
    @Modifying
    @Transactional
    @Query("update ContestSubmission s set s.status = :status, s.verdict = :status, s.cpuTime = :cpuTime, s.memory = :memory, s.passed = :passed, s.total = :total, s.testcaseResults = :payload, s.finishedAt = CURRENT_TIMESTAMP where s.id = :id and s.attemptId = :attempt and s.status = 'Judging'")
    int updateResultIfCurrentAttempt(@Param("id")
    Integer id, @Param("attempt")
    int attempt,
            @Param("status")
            String status, @Param("cpuTime")
            Integer cpuTime, @Param("memory")
            Long memory,
            @Param("passed")
            int passed, @Param("total")
            int total, @Param("payload")
            String payload);

    /**
     * 仅当尝试次数（{@code attemptId}）仍为当前尝试时，更新提交的编译/执行耗时指标。
     *
     * <p>更新字段包括编译起止时间、执行起止时间、校验时间、输出大小、退出码、信号及题包摘要。</p>
     *
     * @return 更新影响的行数：{@code 0} 表示尝试已过期，未发生更新
     */
    @Modifying
    @Transactional
    @Query("update ContestSubmission s set s.compileStartedAt = :compileStart, s.compileFinishedAt = :compileEnd, "
            + "s.executionStartedAt = :executionStart, s.executionFinishedAt = :executionEnd, s.checkedAt = :executionEnd, "
            + "s.outputSize = :outputSize, s.exitCode = :exitCode, s.signal = :signal, s.packageDigest = :packageDigest "
            + "where s.id = :id and s.attemptId = :attempt")
    int updateMetricsIfCurrentAttempt(@Param("id")
    Integer id, @Param("attempt")
    int attempt,
            @Param("compileStart")
            java.time.LocalDateTime compileStart,
            @Param("compileEnd")
            java.time.LocalDateTime compileEnd,
            @Param("executionStart")
            java.time.LocalDateTime executionStart,
            @Param("executionEnd")
            java.time.LocalDateTime executionEnd,
            @Param("outputSize")
            Integer outputSize, @Param("exitCode")
            Integer exitCode,
            @Param("signal")
            Integer signal, @Param("packageDigest")
            String packageDigest);
}
