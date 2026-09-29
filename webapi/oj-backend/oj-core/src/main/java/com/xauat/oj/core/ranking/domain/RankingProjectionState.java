package com.xauat.oj.core.ranking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_projection_state")
public class RankingProjectionState {
    @Id private Integer id = 1;
    @Column(name = "built_at") private LocalDateTime builtAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected RankingProjectionState() {}
}
