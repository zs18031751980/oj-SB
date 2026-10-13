package com.xauat.oj.core.ranking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 排行榜投影状态实体，对应数据库表 {@code ranking_projection_state}。
 *
 * <p>单例状态行（固定主键 id=1），记录排行榜投影最近一次构建时间，
 * 供后台任务判断是否需要重建全局排名。</p>
 */
@Entity
@Table(name = "ranking_projection_state")
public class RankingProjectionState {
    /** 固定主键，单例行恒为 1。 */
    @Id
    private Integer id = 1;
    /** 投影最后构建时间。 */
    @Column(name = "built_at")
    private LocalDateTime builtAt;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    protected RankingProjectionState() {
    }
}
