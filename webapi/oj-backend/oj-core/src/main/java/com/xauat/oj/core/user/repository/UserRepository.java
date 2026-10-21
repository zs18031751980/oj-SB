package com.xauat.oj.core.user.repository;

import com.xauat.oj.core.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储。
 * <p>管理用户实体 {@link User} 的持久化，支持按用户名、邮箱及第三方登录标识查询；
 * 同时支持 Specification 动态条件分页（管理端用户列表）。</p>
 */
public interface UserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {
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

    /**
     * 统计启用状态的用户数（数据库聚合，避免全量加载）。
     *
     * @return 启用用户数量
     */
    long countByActiveTrue();

    /**
     * 查询最近注册的 5 个用户（按主键倒序）。
     *
     * @return 最近用户列表
     */
    List<User> findTop5ByOrderByIdDesc();
}
