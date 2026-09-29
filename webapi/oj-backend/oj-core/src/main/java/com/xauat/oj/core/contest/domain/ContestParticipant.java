package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_participants", uniqueConstraints = @UniqueConstraint(name = "uq_contest_participant", columnNames = {"contest_id", "user_id"}))
public class ContestParticipant extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    private int score;
    private Integer rank;
    protected ContestParticipant() {}
    public static ContestParticipant join(Contest contest, User user) { ContestParticipant item = new ContestParticipant(); item.contest = contest; item.user = user; return item; }
    public Integer getContestId() { return contest == null ? null : contest.getId(); }
    public Integer getUserId() { return user == null ? null : user.getId(); }
    public int getScore() { return score; }
    public Integer getRank() { return rank; }
}
