package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛操作审计实体，对应数据库表 {@code contest_audit}。
 *
 * <p>记录对比赛执行的管理操作（如发布、取消、改规则等），保留操作人、原因与上下文载荷，
 * 用于追溯与合规审计。</p>
 */
@Entity
@Table(name = "contest_audit")
public class ContestAudit extends BaseEntity {
    /** 被操作的比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 操作人（用户）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;
    /** 操作动作标识，如 PUBLISH、CANCEL 等。 */
    @Column(nullable = false)
    private String action;
    /** 操作原因说明。 */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;
    /** 操作上下文载荷（JSON 文本），默认 {}。 */
    @Column(nullable = false, columnDefinition = "text")
    private String payload = "{}";
    protected ContestAudit() {
    }
    /** 构建一条审计记录；payload 为空时置为 {}。 */
    public static ContestAudit of(Contest contest, User actor, String action, String reason, String payload) {
        ContestAudit item = new ContestAudit();
        item.contest = contest;
        item.actor = actor;
        item.action = action;
        item.reason = reason;
        item.payload = payload == null ? "{}" : payload;
        return item;
    }
    public Integer getContestId() {
        return contest.getId();
    }
    /** 返回操作人用户 ID；仅取代理标识，不触发额外查询。 */
    public Integer getActorId() {
        return actor == null ? null : actor.getId();
    }
    public String getAction() {
        return action;
    }
    public String getReason() {
        return reason;
    }
    public String getPayload() {
        return payload;
    }
}
