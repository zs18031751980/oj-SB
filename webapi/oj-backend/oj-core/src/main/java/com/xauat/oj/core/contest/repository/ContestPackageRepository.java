package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 赛事题目包（Package）仓储。
 * <p>管理赛事题目打包实体 {@link ContestPackage}，主键为字符串类型的包标识，用于题包校验与管理。</p>
 */
public interface ContestPackageRepository extends JpaRepository<ContestPackage, String> {
    /**
     * 按校验状态集合查询最近更新的最多 50 个题目包，按更新时间正序（最旧优先处理）。
     *
     * @param states 校验状态集合
     * @return 最多 50 条题目包列表，无匹配时返回空列表
     */
    List<ContestPackage> findTop50ByValidationStateInOrderByUpdatedAtAsc(Collection<String> states);

    /**
     * 查询某题目关联的全部题目包。
     *
     * @param problemId 题目 ID
     * @return 题目包列表，无数据时返回空列表
     */
    List<ContestPackage> findByProblem_Id(Integer problemId);
}
