package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_team_members", uniqueConstraints = @UniqueConstraint(name = "uq_contest_team_member", columnNames = {"contest_id", "user_id"}))
public class ContestTeamMember extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id") private ContestTeam team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    protected ContestTeamMember() {}
    public static ContestTeamMember create(Contest contest, ContestTeam team, User user) { ContestTeamMember item = new ContestTeamMember(); item.contest = contest; item.team = team; item.user = user; return item; }
    public Integer getContestId() { return contest.getId(); }
    public Integer getTeamId() { return team.getId(); }
    public Integer getUserId() { return user.getId(); }
}
