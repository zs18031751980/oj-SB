package com.xauat.oj.core.problem.repository;
import com.xauat.oj.core.problem.domain.Testcase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TestcaseRepository extends JpaRepository<Testcase,Integer> { List<Testcase> findByProblemIdOrderBySortOrderAscIdAsc(Integer problemId); }
