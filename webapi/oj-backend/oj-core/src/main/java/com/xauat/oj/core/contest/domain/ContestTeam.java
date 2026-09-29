package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_teams")
public class ContestTeam extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "captain_id") private User captain;
    @Column(nullable = false, length = 120) private String name;
    protected ContestTeam() {}
    public static ContestTeam create(Contest contest, User captain, String name) { ContestTeam item = new ContestTeam(); item.contest = contest; item.captain = captain; item.name = name; return item; }
    public Integer getContestId() { return contest.getId(); }
    public String getName() { return name; }
    public Integer getId() { return super.getId(); }
}
