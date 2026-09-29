package com.xauat.oj.api.submission;

import com.xauat.oj.core.submission.domain.SubmissionOutbox;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** DB 事务提交后再投递 Redis；Redis 暂时不可用时由下一轮继续重试。 */
@Component
public class SubmissionOutboxDispatcher {
    private final SubmissionOutboxRepository outboxes;
    private final JudgeQueue queue;

    public SubmissionOutboxDispatcher(SubmissionOutboxRepository outboxes, JudgeQueue queue) {
        this.outboxes = outboxes;
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        for (SubmissionOutbox outbox : outboxes.claimPending()) {
            try {
                queue.publish("oj:judge:queue", outbox.getSubmission().getJobId());
                outbox.markDispatched();
                outboxes.save(outbox);
            } catch (RuntimeException exception) {
                outbox.markFailed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                outboxes.save(outbox);
            }
        }
    }
}
