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

    public ProviderUserSync(UserRepository users, AuthSessionRepository sessions) { this.users = users; this.sessions = sessions; }

    @Transactional
    public User sync(String provider, String providerId, JsonNode profile, String username, String email, String avatar) {
        String incomingRole = OidcClaims.highestRole(profile);
        Boolean status = OidcClaims.accountStatus(profile);
        var existing = users.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (username != null && !username.isBlank() && !username.equals(user.getUsername())
                    && users.findByUsername(username).filter(other -> !other.getId().equals(user.getId())).isEmpty()) {
                user.setUsername(username);
            }
            if (email != null && !email.isBlank() && !email.equals(user.getEmail())
                    && users.findByEmail(email).filter(other -> !other.getId().equals(user.getId())).isEmpty()) {
                user.setEmail(email);
            }
            if (avatar != null && !avatar.isBlank()) user.setAvatarUrl(avatar);
            user.setProviderRole(incomingRole);
            String effective = user.getLocalRole() != null && !user.getLocalRole().isBlank() ? user.getLocalRole() : incomingRole;
            boolean roleChanged = !effective.equals(user.getRole());
            user.setRole(effective);
            if (Boolean.FALSE.equals(status)) user.setActive(false);
            user.markLogin();
            users.save(user);
            if (Boolean.FALSE.equals(status) || roleChanged) sessions.revokeByUserId(user.getId());
            return user;
        }
        String finalUsername = username == null || username.isBlank() ? provider + "_" + providerId : username;
        if (users.findByUsername(finalUsername).isPresent()) finalUsername = finalUsername + "_" + providerId;
        String finalEmail = email;
        if (finalEmail != null && !finalEmail.isBlank() && users.findByEmail(finalEmail).isPresent()) finalEmail = null;
        User user = User.external(finalUsername, finalEmail, provider, providerId, avatar);
        user.setRole(incomingRole);
        user.setProviderRole(incomingRole);
        if (Boolean.FALSE.equals(status)) user.setActive(false);
        user.markLogin();
        return users.save(user);
    }
}
