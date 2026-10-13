package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 比赛事件实体，对应数据库表 {@code contest_events}。
 *
 * <p>记录比赛过程中产生的事件（判题结果、澄清、封榜等），用于驱动实时推送与事件回放。
 * {@code audience} 表示事件可见范围（如 jury/裁判、participant/参赛者、user/指定用户）。</p>
 */
@Entity
@Table(name = "contest_events")
public class ContestEvent extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    /** 事件类型标识。 */
    @Column(nullable = false) private String kind;
    /** 事件可见范围，默认 jury（仅裁判可见）。 */
    @Column(nullable = false) private String audience = "jury";
    /** 指定接收者用户 ID（当 audience 为定向用户时使用）。 */
    @Column(name = "recipient_id") private Integer recipientId;
    /** 事件载荷（JSON 文本）。 */
    @Column(nullable = false, columnDefinition = "text") private String payload;
    protected ContestEvent() {}
    /** 构建一条比赛事件。 */
    public static ContestEvent of(Contest contest, String kind, String audience, String payload) { ContestEvent item = new ContestEvent(); item.contest = contest; item.kind = kind; item.audience = audience; item.payload = payload; return item; }
    public Integer getContestId() { return contest.getId(); }
    public String getKind() { return kind; }
    public String getAudience() { return audience; }
    public Integer getRecipientId() { return recipientId; }
    public String getPayload() { return payload; }
}
