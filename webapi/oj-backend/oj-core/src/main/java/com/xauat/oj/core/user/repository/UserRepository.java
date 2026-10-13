package com.xauat.oj.core.user.repository;

import com.xauat.oj.core.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 用户仓储。
 * <p>管理用户实体 {@link User} 的持久化，支持按用户名、邮箱及第三方登录标识查询。</p>
 */
public interface UserRepository extends JpaRepository<User, Integer> {
    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 匹配的用户，未找到时返回空 {@code Optional}
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱
     * @return 匹配的用户，未找到时返回空 {@code Optional}
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据第三方登录提供方与提供方用户 ID 查询用户（OAuth 关联）。
     *
     * @param provider 登录提供方（如 github、gitee）
     * @param providerId 提供方侧的用户标识
     * @return 匹配的用户，未找到时返回空 {@code Optional}
     */
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
