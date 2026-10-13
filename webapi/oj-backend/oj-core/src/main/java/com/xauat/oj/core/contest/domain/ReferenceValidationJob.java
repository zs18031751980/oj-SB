package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;

/**
 * 参考答案校验任务实体，对应数据库表 {@code reference_validation_jobs}。
 *
 * <p>为比赛题目生成校验任务，使用标准答案验证题包测试点是否正确。
 * {@code state} 取值：PENDING（待处理）、QUEUED（已入队）、DISPATCHED（已派发）、DONE（已完成）。</p>
 */
@Entity
@Table(name = "reference_validation_jobs")
public class ReferenceValidationJob {
    /** 任务主键，长度 64 的标识。 */
    @Id @Column(length = 64) private String id;
    /** 待校验的比赛题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private ContestProblem problem;
    /** 对应的题目校验版本号。 */
    @Column(nullable = false) private int version;
    /** 任务状态，默认 PENDING。 */
    @Column(nullable = false, length = 20) private String state = "PENDING";
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected ReferenceValidationJob() {}
    /** 为题目创建校验任务，记录当前校验版本。 */
    public static ReferenceValidationJob create(String id, ContestProblem problem) { ReferenceValidationJob item = new ReferenceValidationJob(); item.id = id; item.problem = problem; item.version = problem.getValidationVersion(); item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    /** 返回任务 ID。 */
    public String getId() { return id; }
    /** 返回题目 ID。 */
    public Integer getProblemId() { return problem == null ? null : problem.getId(); }
    /** 返回校验版本。 */
    public int getVersion() { return version; }
    /** 返回任务状态。 */
    public String getState() { return state; }
    /** 标记为已入队。 */
    public void markQueued() { state = "QUEUED"; updatedAt = java.time.LocalDateTime.now(); }
    /** 标记为已派发。 */
    public void markDispatched() { state = "DISPATCHED"; updatedAt = java.time.LocalDateTime.now(); }
    /** 标记为已完成。 */
    public void markDone() { state = "DONE"; updatedAt = java.time.LocalDateTime.now(); }
}
