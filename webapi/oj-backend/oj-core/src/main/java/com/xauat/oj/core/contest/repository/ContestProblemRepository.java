package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 赛事题目关联仓储。
 * <p>管理赛事与题目的关联实体 {@link ContestProblem}（赛事内题目配置）的持久化与查询。</p>
 */
public interface ContestProblemRepository extends JpaRepository<ContestProblem, Integer> {
    /**
     * 查询某赛事的全部题目，按排序值 {@code sortOrder} 正序、主键正序排列。
     *
     * @param contestId 赛事 ID
     * @return 赛事题目列表，按配置顺序排列，无数据时返回空列表
     */
    List<ContestProblem> findByContest_IdOrderBySortOrderAscIdAsc(Integer contestId);

    /**
     * 按主键与赛事 ID 查询赛事题目关联。
     *
     * @param id 赛事题目关联 ID
     * @param contestId 赛事 ID
     * @return 匹配的关联记录，未找到时返回空 {@code Optional}
     */
    java.util.Optional<ContestProblem> findByIdAndContest_Id(Integer id, Integer contestId);

    /**
     * 删除指定赛事下的全部题目关联。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestProblem p where p.contest.id = :contestId")
    int deleteByContestId(@Param("contestId")
    Integer contestId);
}
