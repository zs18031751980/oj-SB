package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "judge_dead_letters")
public class JudgeDeadLetter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, length = 100) private String queue;
    @Column(name = "job_id", nullable = false, length = 64) private String jobId;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(nullable = false, length = 20) private String state = "OPEN";
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    protected JudgeDeadLetter() {}
    public static JudgeDeadLetter create(String queue, String jobId, String reason) { var item = new JudgeDeadLetter(); item.queue = queue; item.jobId = jobId; item.reason = reason == null ? "unknown" : reason; item.createdAt = LocalDateTime.now(); return item; }
    public Integer getId() { return id; }
    public String getQueue() { return queue; }
    public String getJobId() { return jobId; }
    public String getReason() { return reason; }
    public String getState() { return state; }
    public void markRetried() { state = "RETRIED"; resolvedAt = LocalDateTime.now(); }
}
