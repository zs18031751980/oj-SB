package com.xauat.oj.api.submission;

import com.xauat.oj.common.constant.JudgeQueues;
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

    /**
     * 定时扫描待投递 outbox 并投递到普通判题队列。
     *
     * <p>每条记录投递成功则标记 DISPATCHED；失败则记录原因，下一轮继续重试，
     * 从而在 Redis 短暂不可用时保证最终投递。</p>
     */
    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        for (SubmissionOutbox outbox : outboxes.claimPending()) {
            try {
                // publishOnce 保证同一 jobId 只入队一次；7 天过期兜底。
                queue.publishOnce(JudgeQueues.REGULAR, outbox.getSubmission().getJobId(), 604800);
                outbox.markDispatched();
                outboxes.save(outbox);
            } catch (RuntimeException exception) {
                // 记录失败原因，留待下一轮重试。
                outbox.markFailed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                outboxes.save(outbox);
            }
        }
    }
}
