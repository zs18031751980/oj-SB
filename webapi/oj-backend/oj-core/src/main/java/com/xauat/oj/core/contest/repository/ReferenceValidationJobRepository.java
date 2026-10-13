package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 参考答案校验任务仓储。
 * <p>管理参考答案校验任务实体 {@link ReferenceValidationJob}，主键为字符串类型的任务标识。</p>
 */
public interface ReferenceValidationJobRepository extends JpaRepository<ReferenceValidationJob, String> {
    /**
     * 查询某题目关联的全部校验任务，按创建时间倒序（最新在前）。
     *
     * @param problemId 题目 ID
     * @return 校验任务列表，无数据时返回空列表
     */
    List<ReferenceValidationJob> findByProblem_IdOrderByCreatedAtDesc(Integer problemId);

    /**
     * 按状态查询最早创建的 20 个校验任务，按创建时间正序（最旧优先处理）。
     *
     * @param state 任务状态
     * @return 最多 20 条任务列表，无匹配时返回空列表
     */
    List<ReferenceValidationJob> findTop20ByStateOrderByCreatedAtAsc(String state);

    /**
     * 统计指定状态的校验任务数量。
     *
     * @param state 任务状态
     * @return 该状态的任务数量
     */
    long countByState(String state);

    /**
     * 查询当前最早的待处理（{@code PENDING}）校验任务创建时间，用于监控任务积压。
     *
     * @return 最早的 {@code PENDING} 任务创建时间，无待处理任务时返回 {@code null}
     */
    @org.springframework.data.jpa.repository.Query("select min(j.createdAt) from ReferenceValidationJob j where j.state = 'PENDING'")
    java.time.LocalDateTime oldestPending();
}
