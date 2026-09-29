package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_audit")
public class ContestAudit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false) private String action;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(nullable = false, columnDefinition = "text") private String payload = "{}";
    protected ContestAudit() {}
    public static ContestAudit of(Contest contest, User actor, String action, String reason, String payload) { ContestAudit item = new ContestAudit(); item.contest = contest; item.actor = actor; item.action = action; item.reason = reason; item.payload = payload == null ? "{}" : payload; return item; }
    public Integer getContestId() { return contest.getId(); }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public String getPayload() { return payload; }
}
