package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.domain.ContestRole;
import com.xauat.oj.core.contest.repository.ContestRoleRepository;
import com.xauat.oj.core.user.domain.User;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 比赛内权限：不是全局角色，而是 User + Contest + Capability。
 * manager 拥有全部权限，其余由 contest_roles 的 director/jury/setter/operator 授予。
 */
@Service
public class ContestPermissionService {
    private final ContestRoleRepository roles;

    public ContestPermissionService(ContestRoleRepository roles) {
        this.roles = roles;
    }

    /**
     * 判断用户在指定比赛是否拥有某项能力。
     * 全局 manager 直接放行；否则查询 contest_roles，按角色映射到能力集合。
     *
     * @param contestId  比赛 ID
     * @param actor      当前用户，可能为 {@code null}
     * @param capability 待校验的能力，如 control/jury/rejudge/package/health
     * @return 是否授权
     */
    public boolean allowed(Integer contestId, User actor, String capability) {
        // 匿名或已停用账号一律拒绝
        if (actor == null || !actor.isActive())
            return false;
        if ("manager".equals(actor.getRole()))
            return true;
        ContestRole role = roles.findByContest_IdAndUser_Id(contestId, actor.getId()).orElse(null);
        if (role == null)
            return false;
        // 比赛内角色 → 能力集合：director 全权，jury 可答疑与复判，setter 管题包，operator 只看健康度
        Set<String> grants = switch (role.getRole() == null ? "none" : role.getRole()) {
            case "director" -> Set.of("control", "jury", "rejudge", "package");
            case "jury" -> Set.of("jury", "rejudge");
            case "setter" -> Set.of("package");
            case "operator" -> Set.of("health");
            default -> Set.of();
        };
        return grants.contains(capability);
    }

    /**
     * 与 {@link #allowed} 相同，但无权限时抛出 FORBIDDEN 异常。
     *
     * @param contestId  比赛 ID
     * @param actor      当前用户
     * @param capability 待校验的能力
     */
    public void require(Integer contestId, User actor, String capability) {
        if (!allowed(contestId, actor, capability)) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "没有此比赛的操作权限");
        }
    }
}
