package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 判题死信实体，对应数据库表 {@code judge_dead_letters}。
 *
 * <p>记录多次重试后仍无法处理的判题任务，便于人工排查与重投。
 * {@code state} 取值：OPEN（待处理）、RETRIED（已重投）。</p>
 */
@Entity
@Table(name = "judge_dead_letters")
public class JudgeDeadLetter {
    /** 主键 ID，自增。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    /** 来源队列名称。 */
    @Column(nullable = false, length = 100) private String queue;
    /** 关联的判题任务 ID。 */
    @Column(name = "job_id", nullable = false, length = 64) private String jobId;
    /** 进入死信的原因。 */
    @Column(nullable = false, columnDefinition = "text") private String reason;
    /** 处理状态，默认 OPEN。 */
    @Column(nullable = false, length = 20) private String state = "OPEN";
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    /** 解决/重投时间。 */
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    protected JudgeDeadLetter() {}
    /** 记录一条死信；reason 为空时记为 unknown。 */
    public static JudgeDeadLetter create(String queue, String jobId, String reason) { var item = new JudgeDeadLetter(); item.queue = queue; item.jobId = jobId; item.reason = reason == null ? "unknown" : reason; item.createdAt = LocalDateTime.now(); return item; }
    /** 返回主键 ID。 */
    public Integer getId() { return id; }
    /** 返回来源队列。 */
    public String getQueue() { return queue; }
    /** 返回判题任务 ID。 */
    public String getJobId() { return jobId; }
    /** 返回死信原因。 */
    public String getReason() { return reason; }
    /** 返回处理状态。 */
    public String getState() { return state; }
    /** 标记为已重投并记录解决时间。 */
    public void markRetried() { state = "RETRIED"; resolvedAt = LocalDateTime.now(); }
}
