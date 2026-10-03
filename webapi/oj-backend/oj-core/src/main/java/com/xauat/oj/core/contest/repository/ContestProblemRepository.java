package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, Integer> {
    List<ContestProblem> findByContest_IdOrderBySortOrderAscIdAsc(Integer contestId);
    java.util.Optional<ContestProblem> findByIdAndContest_Id(Integer id, Integer contestId);

    @Modifying
    @Query("delete from ContestProblem p where p.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
}
