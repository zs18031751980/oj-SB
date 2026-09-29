package com.xauat.oj.core.contest.repository;
import com.xauat.oj.core.contest.domain.ContestTestcase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContestTestcaseRepository extends JpaRepository<ContestTestcase,Integer> { List<ContestTestcase> findByContestProblemIdOrderBySortOrderAscIdAsc(Integer contestProblemId); }
