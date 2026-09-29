package com.xauat.oj.core.auth.domain;

import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_grants")
public class OAuthGrant {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "binding_hash", nullable = false, length = 64) private String bindingHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected OAuthGrant() {}
    public static OAuthGrant create(String id, User user, String bindingHash, LocalDateTime expiresAt) { OAuthGrant item = new OAuthGrant(); item.id = id; item.user = user; item.bindingHash = bindingHash; item.expiresAt = expiresAt; item.createdAt = LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    public User getUser() { return user; }
    public boolean usable(String bindingHash) { return expiresAt.isAfter(LocalDateTime.now()) && this.bindingHash.equals(bindingHash); }
    public String getId() { return id; }
}
