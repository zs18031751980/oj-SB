package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestScoreboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ContestScoreboardSnapshotRepository extends JpaRepository<ContestScoreboardSnapshot,Integer> { Optional<ContestScoreboardSnapshot> findByContestIdAndSnapshotKind(Integer contestId, String snapshotKind); }
