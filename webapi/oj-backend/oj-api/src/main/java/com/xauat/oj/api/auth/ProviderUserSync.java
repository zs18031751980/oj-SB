package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方登录用户同步：解析供应商 claims 得到最高角色与账号状态，
 * 新建用户写入 role/provider_role，已有用户更新 provider_role 且本地角色优先；
 * 供应商停用会停用账号并撤销会话（本地停用不可被登录同步重新激活）。
 */
@Service
public class ProviderUserSync {
    private final UserRepository users;
    private final AuthSessionRepository sessions;

    public ProviderUserSync(UserRepository users, AuthSessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    /**
     * 按供应商身份同步本地用户：存在则更新资料与角色，不存在则创建。
     *
     * @param provider   供应商标识
     * @param providerId 供应商侧唯一用户 id
     * @param profile    原始身份声明
     * @param username   供应商用户名
     * @param email      供应商邮箱
     * @param avatar     头像地址
     * @return 同步后的本地用户
     */
    @Transactional
    public User sync(String provider, String providerId, JsonNode profile, String username, String email,
            String avatar) {
        String incomingRole = OidcClaims.highestRole(profile);
        Boolean status = OidcClaims.accountStatus(profile);
        var existing = users.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            User user = existing.get();
            // 仅在用户名变更且不与其它账号冲突时才更新，避免抢占他人用户名
            if (username != null && !username.isBlank() && !username.equals(user.getUsername())
                    && users.findByUsername(username).filter(other -> !other.getId().equals(user.getId())).isEmpty()) {
                user.setUsername(username);
            }
            // 邮箱同理，需保证全局唯一
            if (email != null && !email.isBlank() && !email.equals(user.getEmail())
                    && users.findByEmail(email).filter(other -> !other.getId().equals(user.getId())).isEmpty()) {
                user.setEmail(email);
            }
            if (avatar != null && !avatar.isBlank())
                user.setAvatarUrl(avatar);
            // providerRole 始终记录供应商角色；本地角色（localRole）优先，实现本地提权不被登录覆盖
            user.setProviderRole(incomingRole);
            String effective = user.getLocalRole() != null && !user.getLocalRole().isBlank()
                    ? user.getLocalRole()
                    : incomingRole;
            boolean roleChanged = !effective.equals(user.getRole());
            user.setRole(effective);
            // 供应商停用会停用本地账号；反之本地停用不会被重新激活（不在此处 setActive(true)）
            if (Boolean.FALSE.equals(status))
                user.setActive(false);
            user.markLogin();
            users.save(user);
            // 停用或角色变动时撤销所有现有会话，使旧令牌立即失效
            if (Boolean.FALSE.equals(status) || roleChanged)
                sessions.revokeByUserId(user.getId());
            return user;
        }
        // 新建用户：用户名缺失时用 provider_providerId 兜底，冲突时附加 providerId 保证唯一
        String finalUsername = username == null || username.isBlank() ? provider + "_" + providerId : username;
        if (users.findByUsername(finalUsername).isPresent())
            finalUsername = finalUsername + "_" + providerId;
        String finalEmail = email;
        // 邮箱已属于其它账号时置空，避免破坏唯一约束
        if (finalEmail != null && !finalEmail.isBlank() && users.findByEmail(finalEmail).isPresent())
            finalEmail = null;
        User user = User.external(finalUsername, finalEmail, provider, providerId, avatar);
        user.setRole(incomingRole);
        user.setProviderRole(incomingRole);
        if (Boolean.FALSE.equals(status))
            user.setActive(false);
        user.markLogin();
        return users.save(user);
    }
}
