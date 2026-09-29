package com.xauat.oj.api.contest;

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
            try { queue.publish("oj:contest:judge:queue", outbox.getSubmission().getJobId()); outbox.markDispatched(); outboxes.save(outbox); }
            catch (RuntimeException e) { outbox.markFailed(e.getMessage()); outboxes.save(outbox); }
        }
    }
}
