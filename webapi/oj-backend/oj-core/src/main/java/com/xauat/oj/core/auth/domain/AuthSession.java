package com.xauat.oj.core.auth.domain;

import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 登录会话（刷新令牌）实体，对应数据库表 {@code auth_sessions}。
 *
 * <p>每个会话保存刷新令牌的哈希值，用于换取新的访问令牌（refresh token 轮换）。
 * 数据库只存储哈希与加密后的恢复凭证，不落明文刷新令牌。</p>
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    /** 会话主键，长度 64 的随机标识。 */
    @Id
    @Column(length = 64)
    private String id;
    /** 所属用户（多对一，懒加载）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    /** 所属用户 ID 冗余列，只读（不参与插入/更新）。 */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Integer userId;
    /** 当前有效刷新令牌的哈希。 */
    @Column(name = "refresh_hash", nullable = false, length = 64)
    private String refreshHash;
    /** 上一版刷新令牌的哈希，用于响应丢失时的重试恢复。 */
    @Column(name = "previous_refresh_hash", length = 64)
    private String previousRefreshHash;
    /** 触发本次轮换的刷新请求 ID，用于幂等去重。 */
    @Column(name = "refresh_request_id", length = 128)
    private String refreshRequestId;
    /** 加密保存的新刷新令牌对，供响应丢失时恢复，避免明文入库。 */
    @Column(name = "refresh_retry_ciphertext", columnDefinition = "text")
    private String refreshRetryCiphertext;
    /** 上一版令牌可恢复的截止时间。 */
    @Column(name = "refresh_retry_until")
    private LocalDateTime refreshRetryUntil;
    /** 会话过期时间。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    /** 是否已吊销；true 表示该会话不再可用。 */
    @Column(nullable = false)
    private boolean revoked;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    protected AuthSession() {
    }
    /** 创建会话：写入刷新令牌哈希与过期时间，并初始化审计时间。 */
    public static AuthSession create(String id, User user, String refreshHash, LocalDateTime expiresAt) {
        AuthSession item = new AuthSession();
        item.id = id;
        item.user = user;
        item.refreshHash = refreshHash;
        item.expiresAt = expiresAt;
        item.createdAt = LocalDateTime.now();
        item.updatedAt = item.createdAt;
        return item;
    }
    /** 判断会话是否可用：未吊销、未过期，且哈希匹配当前或重试窗口内的上一版令牌。 */
    public boolean usable(String hash) {
        return !revoked && expiresAt.isAfter(LocalDateTime.now())
                && (refreshHash.equals(hash) || (previousRefreshHash != null && previousRefreshHash.equals(hash)
                        && refreshRetryUntil != null && refreshRetryUntil.isAfter(LocalDateTime.now())));
    }

    /**
     * 刷新成功后的原子轮换：保留上一版哈希，使同一请求在 30 秒内可恢复丢失的响应，
     * 同时记录加密后的新令牌对，数据库不落明文刷新凭证。
     */
    public void applyRotation(String previousHash, String nextHash, String requestId, LocalDateTime retryUntil,
            String recoveryCiphertext) {
        this.previousRefreshHash = previousHash;
        this.refreshHash = nextHash;
        this.refreshRequestId = requestId;
        this.refreshRetryUntil = retryUntil;
        this.refreshRetryCiphertext = recoveryCiphertext;
        this.updatedAt = LocalDateTime.now();
    }

    /** 吊销会话（登出/失效处理），并刷新更新时间。 */
    public void revoke() {
        revoked = true;
        updatedAt = LocalDateTime.now();
    }
    public String getId() {
        return id;
    }
    public User getUser() {
        return user;
    }
    public Integer getUserId() {
        return userId;
    }
    public String getRefreshHash() {
        return refreshHash;
    }
    public String getPreviousRefreshHash() {
        return previousRefreshHash;
    }
    public String getRefreshRequestId() {
        return refreshRequestId;
    }
    public String getRefreshRetryCiphertext() {
        return refreshRetryCiphertext;
    }
    public LocalDateTime getRefreshRetryUntil() {
        return refreshRetryUntil;
    }
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    public boolean isRevoked() {
        return revoked;
    }
}
