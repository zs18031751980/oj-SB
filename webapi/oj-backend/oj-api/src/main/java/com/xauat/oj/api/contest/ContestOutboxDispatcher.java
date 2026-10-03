package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.domain.ContestJudgeOutbox;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.repository.ContestJudgeOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContestOutboxDispatcher {
    private final ContestJudgeOutboxRepository outboxes;
    private final JudgeQueue queue;
    public ContestOutboxDispatcher(ContestJudgeOutboxRepository outboxes, JudgeQueue queue) { this.outboxes = outboxes; this.queue = queue; }

    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        for (var outbox : outboxes.claimPending()) {
            try { queue.publishOnce(queueFor(outbox.getSubmission()), outbox.getSubmission().getJobId(), 604800); outbox.markDispatched(); outboxes.save(outbox); }
            catch (RuntimeException e) { outbox.markFailed(e.getMessage()); outboxes.save(outbox); }
        }
    }

    /** 复判 → rejudge_queue；正式比赛 → contest_judge_queue；练习/题库提交 → practice_judge_queue。 */
    public static String queueFor(ContestSubmission submission) {
        return queueFor(submission.getRejudgeOfId() != null, submission.isContestEligible());
    }

    public static String queueFor(boolean rejudge, boolean contestEligible) {
        if (rejudge) return JudgeQueues.REJUDGE;
        return contestEligible ? JudgeQueues.CONTEST : JudgeQueues.PRACTICE;
    }
}
