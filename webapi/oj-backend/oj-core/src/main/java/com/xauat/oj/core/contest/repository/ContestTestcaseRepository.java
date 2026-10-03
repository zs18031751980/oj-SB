package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestTestcase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContestTestcaseRepository extends JpaRepository<ContestTestcase, Integer> {
    List<ContestTestcase> findByContestProblem_IdOrderBySortOrderAscIdAsc(Integer contestProblemId);

    @Modifying
    @Query("delete from ContestTestcase t where t.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId") Integer problemId);

    @Modifying
    @Query("delete from ContestTestcase t where t.contestProblem.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
}
