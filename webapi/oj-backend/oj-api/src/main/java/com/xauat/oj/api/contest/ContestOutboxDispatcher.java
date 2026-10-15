package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 比赛判题 outbox 派发器：定时扫描待发送的 {@link ContestJudgeOutbox}，
 * 将判题任务投递到对应判题队列并回写派发/失败状态，
 * 借助 outbox 模式保证“提交落库”与“入队”之间的最终一致性。
 */
@Component
public class ContestOutboxDispatcher {
    private final ContestJudgeOutboxRepository outboxes;
    private final JudgeQueue queue;
    public ContestOutboxDispatcher(ContestJudgeOutboxRepository outboxes, JudgeQueue queue) {
        this.outboxes = outboxes;
        this.queue = queue;
    }

    /**
     * 定时派发：拉取一批待发 outbox，逐条投递到判题队列。
     * 成功则标记已派发，失败则记录错误信息等待下一轮重试。
     */
    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        // claimPending 会锁定一批待发记录，避免多实例重复派发
        // 按 contest+entry 轮转排序后再投递，使正式比赛队列在多个比赛/队伍间公平轮转，避免单场刷屏
        for (var outbox : fairOrder(outboxes.claimPending())) {
            try {
                queue.publishOnce(queueFor(outbox.getSubmission()), outbox.getSubmission().getJobId(), 604800);
                outbox.markDispatched();
                outboxes.save(outbox);
            } catch (RuntimeException e) {
                outbox.markFailed(e.getMessage());
                outboxes.save(outbox);
            }
        }
    }

    /**
     * 公平调度排序：按「比赛 + entry（队伍/个人）」分组，对各组做轮转交错（round-robin），
     * 使同一批 outbox 投递到 FIFO 队列后，消费顺序在多个比赛/队伍间交替，而非让某一方独占队首。
     *
     * @param items 待派发的 outbox（保持原有先后顺序作为组内顺序）
     * @return 轮转交错后的顺序
     */
    static java.util.List<ContestJudgeOutbox> fairOrder(java.util.List<ContestJudgeOutbox> items) {
        if (items == null || items.size() <= 1)
            return items == null ? java.util.List.of() : items;
        java.util.Map<String, java.util.List<ContestJudgeOutbox>> groups = new java.util.LinkedHashMap<>();
        for (var item : items)
            groups.computeIfAbsent(fairnessKey(item), key -> new java.util.ArrayList<>()).add(item);
        java.util.List<ContestJudgeOutbox> ordered = new java.util.ArrayList<>(items.size());
        for (int index = 0;; index++) {
            boolean any = false;
            for (var group : groups.values()) {
                if (index < group.size()) {
                    ordered.add(group.get(index));
                    any = true;
                }
            }
            if (!any)
                break;
        }
        return ordered;
    }

    /** 公平调度键：比赛 ID + entry（有队伍按队伍，否则按个人）。取不到时退化为单一分组。 */
    static String fairnessKey(ContestJudgeOutbox item) {
        var submission = item.getSubmission();
        Integer contestId = submission.getContestId();
        String entry = submission.getTeamId() != null ? "t" + submission.getTeamId() : "u" + submission.getUserId();
        return (contestId == null ? "0" : contestId) + ":" + entry;
    }

    /** 复判 → rejudge_queue；正式比赛 → contest_judge_queue；练习/题库提交 → practice_judge_queue。 */
    public static String queueFor(ContestSubmission submission) {
        // 依据“是否复判”与“是否为正式比赛提交”两个维度选择队列
        return queueFor(submission.getRejudgeOfId() != null, submission.isContestEligible());
    }

    /**
     * 判题队列路由：复判优先级最高，其次正式比赛，最后练习/题库。
     *
     * @param rejudge         是否为复判产生的提交
     * @param contestEligible 是否为正式比赛的合格提交
     * @return 目标判题队列名
     */
    public static String queueFor(boolean rejudge, boolean contestEligible) {
        if (rejudge)
            return JudgeQueues.REJUDGE;
        return contestEligible ? JudgeQueues.CONTEST : JudgeQueues.PRACTICE;
    }
}
