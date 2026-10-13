package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestClarification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事答疑（Clarification）仓储。
 * <p>管理赛事问答/澄清实体 {@link ContestClarification} 的持久化与查询。</p>
 */
public interface ContestClarificationRepository extends JpaRepository<ContestClarification, Integer> {
    /**
     * 查询某赛事的全部答疑，按主键倒序（最新在前）。
     *
     * @param contestId 赛事 ID
     * @return 答疑列表，无数据时返回空列表
     */
    List<ContestClarification> findByContest_IdOrderByIdDesc(Integer contestId);
}
