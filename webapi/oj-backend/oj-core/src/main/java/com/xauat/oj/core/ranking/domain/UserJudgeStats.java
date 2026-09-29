package com.xauat.oj.core.ranking.domain;

import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "user_judge_stats")
public class UserJudgeStats {
    @Id @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    private int rank;
    @Column(name = "solved_count") private int solvedCount;
    private int rating;
    @Column(name = "easy_count") private int easyCount;
    @Column(name = "medium_count") private int mediumCount;
    @Column(name = "hard_count") private int hardCount;
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected UserJudgeStats() {}
    public Integer getUserId() { return user == null ? null : user.getId(); }
    public int getRank() { return rank; }
    public int getSolvedCount() { return solvedCount; }
    public int getRating() { return rating; }
}
