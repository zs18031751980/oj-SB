package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_events")
public class ContestEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @Column(nullable = false) private String kind;
    @Column(nullable = false) private String audience = "jury";
    @Column(name = "recipient_id") private Integer recipientId;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    protected ContestEvent() {}
    public static ContestEvent of(Contest contest, String kind, String audience, String payload) { ContestEvent item = new ContestEvent(); item.contest = contest; item.kind = kind; item.audience = audience; item.payload = payload; return item; }
    public Integer getContestId() { return contest.getId(); }
    public String getKind() { return kind; }
    public String getAudience() { return audience; }
    public String getPayload() { return payload; }
}
