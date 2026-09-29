package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Integer> {
    Optional<ContestParticipant> findByContestIdAndUserId(Integer contestId, Integer userId);
    long countByContestId(Integer contestId);
}
