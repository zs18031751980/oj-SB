package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, Integer> {
    List<ContestProblem> findByContestIdOrderBySortOrderAscIdAsc(Integer contestId);
    java.util.Optional<ContestProblem> findByIdAndContestId(Integer id, Integer contestId);
}
