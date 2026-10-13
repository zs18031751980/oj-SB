package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.Judgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 评测结果仓储。
 * <p>管理赛事提交的评测结果实体 {@link Judgement} 的持久化与批量清理。</p>
 */
public interface JudgementRepository extends JpaRepository<Judgement, Integer> {
    /**
     * 查询某次提交的全部评测结果，按主键倒序（最新结果在前）。
     *
     * @param submissionId 提交 ID
     * @return 评测结果列表，无数据时返回空列表
     */
    List<Judgement> findBySubmissionIdOrderByIdDesc(Integer submissionId);

    /**
     * 删除指定赛事题目关联下的全部评测结果。
     *
     * @param problemId 赛事题目关联 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from Judgement j where j.submission.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId")
    Integer problemId);

    /**
     * 删除指定赛事下的全部评测结果。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from Judgement j where j.submission.contest.id = :contestId")
    int deleteByContestId(@Param("contestId")
    Integer contestId);
}
