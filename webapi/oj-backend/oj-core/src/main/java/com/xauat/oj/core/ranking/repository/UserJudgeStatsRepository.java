package com.xauat.oj.core.ranking.repository;

import com.xauat.oj.core.ranking.domain.UserJudgeStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJudgeStatsRepository extends JpaRepository<UserJudgeStats, Integer> {
}
