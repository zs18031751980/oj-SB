package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事审计日志仓储。
 * <p>管理赛事操作审计实体 {@link ContestAudit}，用于记录与增量拉取审计日志。</p>
 */
public interface ContestAuditRepository extends JpaRepository<ContestAudit, Integer> {
    /**
     * 查询某赛事的全部审计记录，按主键倒序（最新在前）。
     *
     * @param contestId 赛事 ID
     * @return 审计记录列表，无数据时返回空列表
     */
    List<ContestAudit> findByContest_IdOrderByIdDesc(Integer contestId);

    /**
     * 增量查询主键大于指定游标的最多 1000 条审计记录，按主键正序。
     *
     * @param id 游标，仅返回主键大于该值的记录
     * @return 最多 1000 条审计记录列表，无新记录时返回空列表
     */
    List<ContestAudit> findTop1000ByIdGreaterThanOrderByIdAsc(Integer id);
}
