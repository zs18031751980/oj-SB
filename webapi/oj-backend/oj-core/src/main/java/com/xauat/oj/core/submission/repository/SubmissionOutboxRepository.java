package com.xauat.oj.core.submission.repository;

import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 提交发件箱（Outbox）仓储。
 * <p>管理提交异步下发记录实体 {@link SubmissionOutbox}，配合评测调度实现事务性消息投递。</p>
 */
public interface SubmissionOutboxRepository extends JpaRepository<SubmissionOutbox, Integer> {
    /**
     * 领取待处理（{@code PENDING}）的提交下发消息。
     *
     * <p>最多领取 100 条，按主键正序；使用原生 SQL 的 {@code FOR UPDATE SKIP LOCKED} 实现并发安全领取，
     * 已锁定的行会被跳过。</p>
     *
     * <p>原生 SQL 自带 FOR UPDATE SKIP LOCKED，不能再叠加 @Lock。</p>
     *
     * @return 本次领取到的待处理消息列表
     */
    @Query(value = "select * from submission_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    java.util.List<SubmissionOutbox> claimPending();

    /**
     * 统计指定状态的消息数量。
     *
     * @param state 消息状态
     * @return 该状态的消息条数
     */
    long countByState(String state);

    /**
     * 删除指定时间之前已下发（{@code DISPATCHED}）的消息。
     *
     * @param cutoff 截止时间，更新时间早于该时间且已下发的记录会被删除
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from SubmissionOutbox o where o.state = 'DISPATCHED' and o.updatedAt < :cutoff")
    int deleteDispatchedBefore(@Param("cutoff")
    LocalDateTime cutoff);

    /**
     * 查询当前最早的待处理消息创建时间，用于监控下发积压。
     *
     * @return 最早的 {@code PENDING} 消息创建时间，无待处理消息时返回 {@code null}
     */
    @Query("select min(o.createdAt) from SubmissionOutbox o where o.state = 'PENDING'")
    LocalDateTime oldestPending();
}
