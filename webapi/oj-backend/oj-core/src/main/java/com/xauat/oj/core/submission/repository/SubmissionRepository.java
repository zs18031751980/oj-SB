package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 提交记录仓储。
 * <p>管理普通（非赛事）提交实体 {@link Submission} 的持久化与查询。</p>
 */
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {
    /**
     * 查询某用户的全部提交，按主键倒序（最新提交在前）。
     *
     * @param userId 用户 ID
     * @return 提交列表，无数据时返回空列表
     */
    List<Submission> findByUser_IdOrderByIdDesc(Integer userId);

    /**
     * 数据库分页查询某用户的提交历史（推荐用于列表接口，避免全量加载）。
     *
     * @param userId   用户 ID
     * @param pageable 分页与排序参数
     * @return 当前页数据及总数
     */
    org.springframework.data.domain.Page<Submission> findByUser_Id(Integer userId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 根据评测任务 ID（{@code jobId}）查询提交。
     *
     * @param jobId 评测任务 ID
     * @return 匹配的提交，未找到时返回空 {@code Optional}
     */
    Optional<Submission> findByJobId(String jobId);

    /**
     * 根据用户与幂等键查询提交，用于提交接口的幂等去重。
     *
     * @param userId 用户 ID
     * @param idempotencyKey 幂等键
     * @return 匹配的提交，未找到时返回空 {@code Optional}
     */
    Optional<Submission> findByUser_IdAndIdempotencyKey(Integer userId, String idempotencyKey);

    /**
     * 统计某用户在指定状态集合下的提交数量。
     *
     * @param userId 用户 ID
     * @param statuses 状态集合（如评测中、通过等）
     * @return 满足条件的提交条数
     */
    long countByUser_IdAndStatusIn(Integer userId, Collection<String> statuses);
}
