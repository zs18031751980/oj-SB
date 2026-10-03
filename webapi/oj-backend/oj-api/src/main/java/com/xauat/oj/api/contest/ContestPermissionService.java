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

    public ContestPermissionService(ContestRoleRepository roles) { this.roles = roles; }

    public boolean allowed(Integer contestId, User actor, String capability) {
        if (actor == null || !actor.isActive()) return false;
        if ("manager".equals(actor.getRole())) return true;
        ContestRole role = roles.findByContest_IdAndUser_Id(contestId, actor.getId()).orElse(null);
        if (role == null) return false;
        Set<String> grants = switch (role.getRole() == null ? "none" : role.getRole()) {
            case "director" -> Set.of("control", "jury", "rejudge", "package");
            case "jury" -> Set.of("jury", "rejudge");
            case "setter" -> Set.of("package");
            case "operator" -> Set.of("health");
            default -> Set.of();
        };
        return grants.contains(capability);
    }

    public void require(Integer contestId, User actor, String capability) {
        if (!allowed(contestId, actor, capability)) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "没有此比赛的操作权限");
        }
    }
}
