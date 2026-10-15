package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛角色实体，对应数据库表 {@code contest_roles}。
 *
 * <p>授予用户在指定比赛中的角色（如裁判、管理员等），
 * 通过 {@code (contest_id, user_id)} 唯一约束保证每用户每比赛仅一个角色。</p>
 */
@Entity
@Table(name = "contest_roles", uniqueConstraints = @UniqueConstraint(name = "uq_contest_role_user", columnNames = {
        "contest_id", "user_id"}))
public class ContestRole extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 被授权用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    /** 角色标识，如 jury（裁判）、admin（管理员）等。 */
    @Column(nullable = false)
    private String role;
    protected ContestRole() {
    }
    /** 为用户授予比赛角色。 */
    public static ContestRole create(Contest contest, User user, String role) {
        ContestRole item = new ContestRole();
        item.contest = contest;
        item.user = user;
        item.role = role;
        return item;
    }
    /** 返回比赛 ID。 */
    public Integer getContestId() {
        return contest.getId();
    }
    /** 返回用户 ID。 */
    public Integer getUserId() {
        return user.getId();
    }
    /** 返回角色标识。 */
    public String getRole() {
        return role;
    }
    /** 覆盖角色标识，用于同用户同比赛角色的幂等更新。 */
    public void setRole(String role) {
        this.role = role;
    }
}
