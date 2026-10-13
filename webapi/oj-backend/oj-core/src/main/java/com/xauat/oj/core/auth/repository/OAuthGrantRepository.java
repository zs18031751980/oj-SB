package com.xauat.oj.core.auth.repository;
import com.xauat.oj.core.auth.domain.OAuthGrant;
import org.springframework.data.jpa.repository.JpaRepository;
/**
 * OAuth 授权记录仓储。
 * <p>管理第三方 OAuth 绑定/授权记录实体 {@link OAuthGrant}，主键为字符串类型的授权标识。</p>
 */
public interface OAuthGrantRepository extends JpaRepository<OAuthGrant, String> {
    /**
     * 根据绑定哈希查询对应的 OAuth 授权记录。
     *
     * @param bindingHash 绑定信息的哈希值
     * @return 匹配的授权记录，未找到时返回空 {@code Optional}
     */
    java.util.Optional<OAuthGrant> findByBindingHash(String bindingHash);
}
