package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事队伍成员仓储。
 * <p>管理赛事队伍成员实体 {@link ContestTeamMember} 的持久化与查询。</p>
 */
public interface ContestTeamMemberRepository extends JpaRepository<ContestTeamMember, Integer> {
    /**
     * 查询某赛事的全部队伍成员，按主键正序。
     *
     * @param contestId 赛事 ID
     * @return 队伍成员列表，无数据时返回空列表
     */
    List<ContestTeamMember> findByContest_IdOrderByIdAsc(Integer contestId);

    /**
     * 查询某赛事中指定用户的队伍成员记录。
     *
     * @param contestId 赛事 ID
     * @param userId 用户 ID
     * @return 匹配的记录，未找到时返回空 {@code Optional}
     */
    java.util.Optional<ContestTeamMember> findByContest_IdAndUser_Id(Integer contestId, Integer userId);
}
