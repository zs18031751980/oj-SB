package com.xauat.oj.core.submission.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 公共提交判题发件箱实体，对应数据库表 {@code submission_outbox}。
 *
 * <p>采用 Outbox 模式保证公共提交与判题任务派发的最终一致性。
 * {@code state} 取值：PENDING（待派发）、DISPATCHED（已派发）。</p>
 */
@Entity
@Table(name = "submission_outbox")
public class SubmissionOutbox extends BaseEntity {
    /** 关联的公共提交（一对一）。 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", unique = true)
    private Submission submission;
    /** 派发状态，默认 PENDING。 */
    @Column(nullable = false, length = 20)
    private String state = "PENDING";
    /** 派发尝试次数。 */
    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;
    /** 最近一次派发失败的错误信息。 */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    /** 派发成功时间。 */
    @Column(name = "dispatched_at")
    private java.time.LocalDateTime dispatchedAt;
    protected SubmissionOutbox() {
    }
    /** 为指定提交创建发件箱记录。 */
    public static SubmissionOutbox of(Submission submission) {
        SubmissionOutbox outbox = new SubmissionOutbox();
        outbox.submission = submission;
        return outbox;
    }
    /** 返回关联提交。 */
    public Submission getSubmission() {
        return submission;
    }
    /** 返回派发状态。 */
    public String getState() {
        return state;
    }
    /** 标记为已派发并记录时间。 */
    public void markDispatched() {
        state = "DISPATCHED";
        dispatchedAt = java.time.LocalDateTime.now();
    }
    /** 记录派发失败：回到 PENDING、累加尝试次数并保存错误。 */
    public void markFailed(String error) {
        state = "PENDING";
        dispatchAttempts++;
        lastError = error;
    }
}
