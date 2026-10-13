package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestTestcase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 赛事测试点仓储。
 * <p>管理赛事题目测试点实体 {@link ContestTestcase} 的持久化与批量清理。</p>
 */
public interface ContestTestcaseRepository extends JpaRepository<ContestTestcase, Integer> {
    /**
     * 查询某赛事题目关联下的全部测试点，按排序值 {@code sortOrder} 正序、主键正序排列。
     *
     * @param contestProblemId 赛事题目关联 ID
     * @return 测试点列表，按执行顺序排列，无数据时返回空列表
     */
    List<ContestTestcase> findByContestProblem_IdOrderBySortOrderAscIdAsc(Integer contestProblemId);

    /**
     * 删除指定赛事题目关联下的全部测试点。
     *
     * @param problemId 赛事题目关联 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestTestcase t where t.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId") Integer problemId);

    /**
     * 删除指定赛事下所有题目的测试点。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestTestcase t where t.contestProblem.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
}
