package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Integer> {
    Optional<ContestParticipant> findByContest_IdAndUser_Id(Integer contestId, Integer userId);
    long countByContest_Id(Integer contestId);

    @Modifying
    @Query("delete from ContestParticipant p where p.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
}
