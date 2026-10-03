package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 将待验证的参考代码任务投递到 testcase_gen_queue，由独立 Worker 消费。 */
@Component
public class ReferenceValidationDispatcher {
    private final ReferenceValidationJobRepository jobs;
    private final JudgeQueue queue;

    public ReferenceValidationDispatcher(ReferenceValidationJobRepository jobs, JudgeQueue queue) {
        this.jobs = jobs; this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        for (var job : jobs.findTop20ByStateOrderByCreatedAtAsc("PENDING")) {
            try { queue.publishOnce(JudgeQueues.TESTCASE_GEN, job.getId(), 604800); job.markDispatched(); jobs.save(job); }
            catch (RuntimeException ignored) { }
        }
    }
}
