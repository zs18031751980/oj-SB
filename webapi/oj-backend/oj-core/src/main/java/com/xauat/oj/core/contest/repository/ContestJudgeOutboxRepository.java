package com.xauat.oj.core.contest.repository;

import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * 赛事评测发件箱（Outbox）仓储。
 * <p>管理赛事提交异步评测下发记录实体 {@link ContestJudgeOutbox}，配合评测调度实现事务性消息投递。</p>
 */
public interface ContestJudgeOutboxRepository extends JpaRepository<ContestJudgeOutbox, Integer> {
    /**
     * 领取待处理（{@code PENDING}）的赛事评测下发消息。
     *
     * <p>最多领取 100 条，按主键正序；使用原生 SQL 的 {@code FOR UPDATE SKIP LOCKED} 实现并发安全领取，
     * 已被其他消费者锁定的行会被跳过。</p>
     *
     * <p>原生 SQL 自带 FOR UPDATE SKIP LOCKED，不能再叠加 @Lock。</p>
     *
     * @return 本次领取到的待处理消息列表
     */
    @Query(value = "select * from contest_judge_outbox where state = 'PENDING' order by id asc limit 100 for update skip locked", nativeQuery = true)
    List<ContestJudgeOutbox> claimPending();

    /**
     * 删除指定赛事题目关联相关的全部下发消息。
     *
     * @param problemId 赛事题目关联 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.submission.contestProblem.id = :problemId")
    int deleteByProblemId(@Param("problemId")
    Integer problemId);

    /**
     * 删除指定赛事相关的全部下发消息。
     *
     * @param contestId 赛事 ID
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.submission.contest.id = :contestId")
    int deleteByContestId(@Param("contestId")
    Integer contestId);

    /**
     * 统计指定状态的下发消息数量。
     *
     * @param state 消息状态
     * @return 该状态的消息条数
     */
    long countByState(String state);

    /**
     * 删除指定时间之前、且提交为最终结果的已下发（{@code DISPATCHED}）消息。
     *
     * <p>仅清理非重判（{@code submission.rejudgeOf} 为空）且提交状态属于给定集合的记录。</p>
     *
     * @param cutoff 截止时间，更新时间早于该时间的记录会被删除
     * @param statuses 允许清理的提交状态集合
     * @return 被删除的记录条数
     */
    @Modifying
    @Query("delete from ContestJudgeOutbox o where o.state = 'DISPATCHED' and o.updatedAt < :cutoff "
            + "and o.submission.rejudgeOf is null and o.submission.status in :statuses")
    int deleteDispatchedFinalBefore(@Param("cutoff")
    java.time.LocalDateTime cutoff,
            @Param("statuses")
            java.util.Collection<String> statuses);

    /**
     * 查询当前最早的待处理消息创建时间，用于监控下发积压。
     *
     * @return 最早的 {@code PENDING} 消息创建时间，无待处理消息时返回 {@code null}
     */
    @Query("select min(o.createdAt) from ContestJudgeOutbox o where o.state = 'PENDING'")
    java.time.LocalDateTime oldestPending();
}
