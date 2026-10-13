package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.RejudgeBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 重判批次仓储。
 * <p>管理赛事重判批次实体 {@link RejudgeBatch} 的持久化与查询。</p>
 */
public interface RejudgeBatchRepository extends JpaRepository<RejudgeBatch, Integer> {
    /**
     * 查询某赛事的全部重判批次，按主键倒序（最新批次在前）。
     *
     * @param contestId 赛事 ID
     * @return 重判批次列表，无数据时返回空列表
     */
    List<RejudgeBatch> findByContest_IdOrderByIdDesc(Integer contestId);

    /**
     * 统计某赛事下处于指定状态的重判批次数量。
     *
     * @param contestId 赛事 ID
     * @param state 批次状态
     * @return 满足条件的批次数量
     */
    long countByContest_IdAndState(Integer contestId, String state);
}
