package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 赛事角色仓储。
 * <p>管理用户在赛事中的角色实体 {@link ContestRole}（如评委、管理员等）的持久化与查询。</p>
 */
public interface ContestRoleRepository extends JpaRepository<ContestRole, Integer> {
    /**
     * 查询某赛事的全部角色配置，按主键正序。
     *
     * @param contestId 赛事 ID
     * @return 角色列表，无数据时返回空列表
     */
    List<ContestRole> findByContest_IdOrderByIdAsc(Integer contestId);

    /**
     * 查询某赛事中指定用户的角色。
     *
     * @param contestId 赛事 ID
     * @param userId 用户 ID
     * @return 匹配的角色，未找到时返回空 {@code Optional}
     */
    java.util.Optional<ContestRole> findByContest_IdAndUser_Id(Integer contestId, Integer userId);
}
