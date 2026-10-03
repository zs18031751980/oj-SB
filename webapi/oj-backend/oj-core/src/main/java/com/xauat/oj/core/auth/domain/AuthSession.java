package com.xauat.oj.core.auth.domain;

import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "user_id", insertable = false, updatable = false) private Integer userId;
    @Column(name = "refresh_hash", nullable = false, length = 64) private String refreshHash;
    @Column(name = "previous_refresh_hash", length = 64) private String previousRefreshHash;
    @Column(name = "refresh_request_id", length = 128) private String refreshRequestId;
    @Column(name = "refresh_retry_ciphertext", columnDefinition = "text") private String refreshRetryCiphertext;
    @Column(name = "refresh_retry_until") private LocalDateTime refreshRetryUntil;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected AuthSession() {}
    public static AuthSession create(String id, User user, String refreshHash, LocalDateTime expiresAt) { AuthSession item = new AuthSession(); item.id = id; item.user = user; item.refreshHash = refreshHash; item.expiresAt = expiresAt; item.createdAt = LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    public boolean usable(String hash) { return !revoked && expiresAt.isAfter(LocalDateTime.now()) && (refreshHash.equals(hash) || (previousRefreshHash != null && previousRefreshHash.equals(hash) && refreshRetryUntil != null && refreshRetryUntil.isAfter(LocalDateTime.now()))); }

    /**
     * 刷新成功后的原子轮换：保留上一版哈希，使同一请求在 30 秒内可恢复丢失的响应，
     * 同时记录加密后的新令牌对，数据库不落明文刷新凭证。
     */
    public void applyRotation(String previousHash, String nextHash, String requestId, LocalDateTime retryUntil, String recoveryCiphertext) {
        this.previousRefreshHash = previousHash;
        this.refreshHash = nextHash;
        this.refreshRequestId = requestId;
        this.refreshRetryUntil = retryUntil;
        this.refreshRetryCiphertext = recoveryCiphertext;
        this.updatedAt = LocalDateTime.now();
    }

    public void revoke() { revoked = true; updatedAt = LocalDateTime.now(); }
    public String getId() { return id; }
    public User getUser() { return user; }
    public Integer getUserId() { return userId; }
    public String getRefreshHash() { return refreshHash; }
    public String getPreviousRefreshHash() { return previousRefreshHash; }
    public String getRefreshRequestId() { return refreshRequestId; }
    public String getRefreshRetryCiphertext() { return refreshRetryCiphertext; }
    public LocalDateTime getRefreshRetryUntil() { return refreshRetryUntil; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
}
