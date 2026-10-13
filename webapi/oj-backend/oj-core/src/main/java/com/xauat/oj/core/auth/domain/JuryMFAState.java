package com.xauat.oj.core.auth.domain;

import jakarta.persistence.*;

/** 只持久化已消费的 TOTP 时间步，跨进程阻止同一验证码重放。 */
@Entity
@Table(name = "jury_mfa_state")
public class JuryMFAState {
    /** 用户名 ID，同时作为主键。 */
    @Id @Column(name = "user_id") private Integer userId;
    /** 最近一次已消费的 TOTP 时间步计数；-1 表示尚未消费。 */
    @Column(name = "last_counter", nullable = false) private long lastCounter = -1;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected JuryMFAState() {}
    /** 为指定用户初始化 MFA 状态。 */
    public static JuryMFAState create(Integer userId) {
        JuryMFAState item = new JuryMFAState();
        item.userId = userId; item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt;
        return item;
    }
    public Integer getUserId() { return userId; }
    public long getLastCounter() { return lastCounter; }
    /** 记录已消费的时间步，推进防重放水位；仅接受更大的计数由调用方保证。 */
    public void consume(long counter) { this.lastCounter = counter; this.updatedAt = java.time.LocalDateTime.now(); }
}
