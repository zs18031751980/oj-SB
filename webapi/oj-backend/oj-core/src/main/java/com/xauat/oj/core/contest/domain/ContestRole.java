package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_roles", uniqueConstraints = @UniqueConstraint(name = "uq_contest_role_user", columnNames = {"contest_id", "user_id"}))
public class ContestRole extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private String role;
    protected ContestRole() {}
    public static ContestRole create(Contest contest, User user, String role) { ContestRole item = new ContestRole(); item.contest = contest; item.user = user; item.role = role; return item; }
    public Integer getContestId() { return contest.getId(); }
    public Integer getUserId() { return user.getId(); }
    public String getRole() { return role; }
}
