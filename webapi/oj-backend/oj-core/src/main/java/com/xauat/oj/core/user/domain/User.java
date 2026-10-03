package com.xauat.oj.core.user.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(length = 50, unique = true)
    private String username;
    @Column(length = 100, unique = true)
    private String email;
    @Column(length = 100)
    private String name;
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(nullable = false, length = 20)
    private String role = "member";
    @Column(name = "provider_role", length = 20)
    private String providerRole;
    @Column(name = "local_role", length = 20)
    private String localRole;
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    @Column(length = 50)
    private String provider;
    @Column(name = "provider_id", length = 255)
    private String providerId;
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    @Column(length = 500)
    private String bio;
    @Column(name = "theme_preference", length = 10)
    private String themePreference = "system";

    protected User() {}

    public static User local(String username, String email, String passwordHash) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }
    public static User external(String username, String email, String provider, String providerId, String avatarUrl) { User user = new User(); user.username = username; user.email = email; user.provider = provider; user.providerId = providerId; user.avatarUrl = avatarUrl; return user; }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }
    public String getProviderRole() { return providerRole; }
    public String getLocalRole() { return localRole; }
    public void setRole(String role) { this.role = role; }
    public void setProviderRole(String providerRole) { this.providerRole = providerRole; }
    public void setUsername(String username) { this.username = username; }
    public boolean isActive() { return active; }
    public String getPasswordHash() { return passwordHash; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getThemePreference() { return themePreference; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void updateProfile(String name, String bio, String themePreference) {
        if (name != null) this.name = name;
        if (bio != null) this.bio = bio;
        if (themePreference != null) this.themePreference = themePreference;
    }
    public String getBio() { return bio; }
    public void setEmail(String email) { this.email = email; }
    public void markLogin() { this.lastLogin = LocalDateTime.now(); }
    public void updatePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { this.active = active; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
