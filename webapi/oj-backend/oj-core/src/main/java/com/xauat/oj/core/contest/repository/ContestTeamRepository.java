package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事队伍仓储。
 * <p>管理赛事队伍实体 {@link ContestTeam} 的持久化与按赛事查询。</p>
 */
public interface ContestTeamRepository extends JpaRepository<ContestTeam, Integer> {
    /**
     * 查询某赛事的全部队伍，按主键正序。
     *
     * @param contestId 赛事 ID
     * @return 队伍列表，无数据时返回空列表
     */
    List<ContestTeam> findByContest_IdOrderByIdAsc(Integer contestId);
}
