package com.xauat.oj.core.problem.repository;

import com.xauat.oj.core.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 题目仓储。
 * <p>管理题目实体 {@link Problem} 的持久化与查询，未声明额外自定义查询方法。</p>
 */
public interface ProblemRepository extends JpaRepository<Problem, Integer> {
}
