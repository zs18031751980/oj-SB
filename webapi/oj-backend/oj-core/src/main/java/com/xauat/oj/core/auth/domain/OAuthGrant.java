package com.xauat.oj.core.auth.domain;

import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OAuth 授权凭证实体，对应数据库表 {@code oauth_grants}。
 *
 * <p>用于第三方 OAuth 登录/绑定的临时授权记录，通过绑定哈希校验请求来源，
 * 并设置过期时间以限制其有效窗口。</p>
 */
@Entity
@Table(name = "oauth_grants")
public class OAuthGrant {
    /** 授权主键，长度 64 的随机标识。 */
    @Id
    @Column(length = 64)
    private String id;
    /** 关联用户（多对一，懒加载）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    /** 绑定校验哈希，用于验证授权请求的合法性。 */
    @Column(name = "binding_hash", nullable = false, length = 64)
    private String bindingHash;
    /** 授权过期时间。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    protected OAuthGrant() {
    }
    /** 创建授权凭证。 */
    public static OAuthGrant create(String id, User user, String bindingHash, LocalDateTime expiresAt) {
        OAuthGrant item = new OAuthGrant();
        item.id = id;
        item.user = user;
        item.bindingHash = bindingHash;
        item.expiresAt = expiresAt;
        item.createdAt = LocalDateTime.now();
        item.updatedAt = item.createdAt;
        return item;
    }
    /** 返回关联用户。 */
    public User getUser() {
        return user;
    }
    /** 判断授权是否可用：未过期且绑定哈希匹配。 */
    public boolean usable(String bindingHash) {
        return expiresAt.isAfter(LocalDateTime.now()) && this.bindingHash.equals(bindingHash);
    }
    /** 返回授权主键。 */
    public String getId() {
        return id;
    }
}
