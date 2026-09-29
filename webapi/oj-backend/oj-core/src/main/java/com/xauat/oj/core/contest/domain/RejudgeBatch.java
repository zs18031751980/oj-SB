package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "rejudge_batches")
public class RejudgeBatch extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(nullable = false) private String state = "PENDING";
    @Column(name = "reviewed_by") private Integer reviewedBy;
    protected RejudgeBatch() {}
    public static RejudgeBatch create(Contest contest, User actor, String reason) { RejudgeBatch item = new RejudgeBatch(); item.contest = contest; item.actor = actor; item.reason = reason; return item; }
    public Integer getContestId() { return contest.getId(); }
    public String getState() { return state; }
    public void review(Integer userId, boolean approve) { reviewedBy = userId; state = approve ? "APPROVED" : "REJECTED"; }
    public boolean approved() { return "APPROVED".equals(state); }
}
