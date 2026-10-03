package com.xauat.oj.core.auth.domain;

import jakarta.persistence.*;

/** 只持久化已消费的 TOTP 时间步，跨进程阻止同一验证码重放。 */
@Entity
@Table(name = "jury_mfa_state")
public class JuryMFAState {
    @Id @Column(name = "user_id") private Integer userId;
    @Column(name = "last_counter", nullable = false) private long lastCounter = -1;
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected JuryMFAState() {}
    public static JuryMFAState create(Integer userId) {
        JuryMFAState item = new JuryMFAState();
        item.userId = userId; item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt;
        return item;
    }
    public Integer getUserId() { return userId; }
    public long getLastCounter() { return lastCounter; }
    public void consume(long counter) { this.lastCounter = counter; this.updatedAt = java.time.LocalDateTime.now(); }
}
