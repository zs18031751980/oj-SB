package com.xauat.oj.core.problem.repository;

import com.xauat.oj.core.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Integer> {
}
