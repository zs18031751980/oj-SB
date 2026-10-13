package com.xauat.oj.core.ranking.repository;

import com.xauat.oj.core.ranking.domain.UserJudgeStats;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户评测统计仓储。
 * <p>管理用户评测维度统计实体 {@link UserJudgeStats} 的持久化，供排行榜等功能读取。</p>
 */
public interface UserJudgeStatsRepository extends JpaRepository<UserJudgeStats, Integer> {
}
