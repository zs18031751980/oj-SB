package com.xauat.oj.core.problem.repository;
import com.xauat.oj.core.problem.domain.Testcase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
/**
 * 题目测试点仓储。
 * <p>管理普通题目的测试点实体 {@link Testcase} 的持久化与按题目查询。</p>
 */
public interface TestcaseRepository extends JpaRepository<Testcase, Integer> {
    /**
     * 查询某题目的全部测试点，按排序值 {@code sortOrder} 正序、主键正序排列。
     *
     * @param problemId 题目 ID
     * @return 测试点列表，按执行顺序排列，无数据时返回空列表
     */
    List<Testcase> findByProblemIdOrderBySortOrderAscIdAsc(Integer problemId);
}
