package com.xauat.oj.core.ranking.repository;

import com.xauat.oj.core.ranking.domain.RankingProjectionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingProjectionStateRepository extends JpaRepository<RankingProjectionState, Integer> {
}
