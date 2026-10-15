package com.xauat.oj.core.user.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 {@code users}。
 *
 * <p>平台账户信息，支持本地注册与第三方（OAuth）登录。
 * {@code role} 取值示例：member（普通成员）、admin（管理员）等；
 * {@code themePreference} 取值：system/light/dark，默认 system。</p>
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    /** 用户名，全局唯一，最长 50 字符。 */
    @Column(length = 50, unique = true)
    private String username;
    /** 邮箱，全局唯一，最长 100 字符。 */
    @Column(length = 100, unique = true)
    private String email;
    /** 显示昵称/真实姓名。 */
    @Column(length = 100)
    private String name;
    /** 密码哈希（BCrypt 等），不落明文。 */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    /** 账号是否启用；false 时禁止登录。 */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    /** 全局角色，默认 member。 */
    @Column(nullable = false, length = 20)
    private String role = "member";
    /** 第三方身份提供方返回的角色（若提供）。 */
    @Column(name = "provider_role", length = 20)
    private String providerRole;
    /** 本系统内分配的角色（若提供）。 */
    @Column(name = "local_role", length = 20)
    private String localRole;
    /** 最近一次登录时间。 */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    /** 第三方登录提供方标识（如 github、gitee）。 */
    @Column(length = 50)
    private String provider;
    /** 第三方提供方内的用户唯一标识。 */
    @Column(name = "provider_id", length = 255)
    private String providerId;
    /** 头像地址。 */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    /** 个人简介。 */
    @Column(length = 500)
    private String bio;
    /** 主题偏好，取值 system/light/dark，默认 system。 */
    @Column(name = "theme_preference", length = 10)
    private String themePreference = "system";

    protected User() {
    }

    /** 创建本地注册用户。 */
    public static User local(String username, String email, String passwordHash) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }
    /** 创建第三方登录用户，绑定提供方信息与头像。 */
    public static User external(String username, String email, String provider, String providerId, String avatarUrl) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.provider = provider;
        user.providerId = providerId;
        user.avatarUrl = avatarUrl;
        return user;
    }

    public String getUsername() {
        return username;
    }
    public String getEmail() {
        return email;
    }
    public String getName() {
        return name;
    }
    public String getAvatarUrl() {
        return avatarUrl;
    }
    public String getRole() {
        return role;
    }
    public String getProviderRole() {
        return providerRole;
    }
    public String getLocalRole() {
        return localRole;
    }
    public void setRole(String role) {
        this.role = role;
    }
    public void setProviderRole(String providerRole) {
        this.providerRole = providerRole;
    }
    /** 设置本地角色覆盖值；传 {@code null} 表示回退使用 provider_role。 */
    public void setLocalRole(String localRole) {
        this.localRole = localRole;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public boolean isActive() {
        return active;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public String getProvider() {
        return provider;
    }
    public String getProviderId() {
        return providerId;
    }
    public String getThemePreference() {
        return themePreference;
    }
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    /** 更新个人资料；null 参数表示不修改。 */
    public void updateProfile(String name, String bio, String themePreference) {
        if (name != null)
            this.name = name;
        if (bio != null)
            this.bio = bio;
        if (themePreference != null)
            this.themePreference = themePreference;
    }
    /** 返回个人简介。 */
    public String getBio() {
        return bio;
    }
    /** 更新邮箱。 */
    public void setEmail(String email) {
        this.email = email;
    }
    /** 记录本次登录时间。 */
    public void markLogin() {
        this.lastLogin = LocalDateTime.now();
    }
    /** 更新密码哈希。 */
    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    /** 启用/禁用账号。 */
    public void setActive(boolean active) {
        this.active = active;
    }
    /** 更新头像地址。 */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
