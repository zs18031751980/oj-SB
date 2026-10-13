package com.xauat.oj.core.ranking.repository;

import com.xauat.oj.core.ranking.domain.RankingProjectionState;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 排行榜投影状态仓储。
 * <p>管理排行榜投影状态实体 {@link RankingProjectionState}，用于记录排行榜计算结果/版本等投影进度信息。</p>
 */
public interface RankingProjectionStateRepository extends JpaRepository<RankingProjectionState, Integer> {
}
