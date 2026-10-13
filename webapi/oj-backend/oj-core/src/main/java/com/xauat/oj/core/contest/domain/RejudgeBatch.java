package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 重判批次实体，对应数据库表 {@code rejudge_batches}。
 *
 * <p>记录一次批量重判的申请与审批；需第二个裁判审核后才生效。
 * {@code state} 取值：PENDING（待审批）、APPLIED（已应用）、CANCELLED（已取消）。</p>
 */
@Entity
@Table(name = "rejudge_batches")
public class RejudgeBatch extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    /** 发起人。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    /** 重判原因。 */
    @Column(nullable = false, columnDefinition = "text") private String reason;
    /** 批次状态，默认 PENDING。 */
    @Column(nullable = false) private String state = "PENDING";
    /** 审核人用户 ID。 */
    @Column(name = "reviewed_by") private Integer reviewedBy;
    protected RejudgeBatch() {}
    /** 发起重判批次。 */
    public static RejudgeBatch create(Contest contest, User actor, String reason) { RejudgeBatch item = new RejudgeBatch(); item.contest = contest; item.actor = actor; item.reason = reason; return item; }
    /** 返回比赛 ID。 */
    public Integer getContestId() { return contest.getId(); }
    /** 返回批次状态。 */
    public String getState() { return state; }
    /** 返回重判原因。 */
    public String getReason() { return reason; }
    /** 返回发起人 ID。 */
    public Integer getActorId() { return actor == null ? null : actor.getId(); }
    /** 是否处于待审批状态。 */
    public boolean isPending() { return "PENDING".equals(state); }
    /** 审批通过并应用重判。 */
    public void apply(Integer reviewerId) { reviewedBy = reviewerId; state = "APPLIED"; }
    /** 审批驳回/取消重判。 */
    public void cancel(Integer reviewerId) { reviewedBy = reviewerId; state = "CANCELLED"; }
}
