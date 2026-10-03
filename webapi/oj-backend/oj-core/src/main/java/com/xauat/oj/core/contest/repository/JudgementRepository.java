package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.Judgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JudgementRepository extends JpaRepository<Judgement, Integer> {
    List<Judgement> findBySubmissionIdOrderByIdDesc(Integer submissionId);

    @Modifying
    @Query("delete from Judgement j where j.submission.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId") Integer problemId);

    @Modifying
    @Query("delete from Judgement j where j.submission.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Integer contestId);
}
