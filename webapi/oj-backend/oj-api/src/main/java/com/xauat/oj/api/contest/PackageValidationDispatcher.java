package com.xauat.oj.api.contest;

import com.xauat.oj.common.constant.JudgeQueues;
import com.xauat.oj.core.contest.repository.ContestPackageRepository;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 将待验证题包投递到 testcase_gen_queue，job_id 形如 package:&lt;digest&gt;。 */
@Component
public class PackageValidationDispatcher {
    private final ContestPackageRepository packages;
    private final JudgeQueue queue;

    public PackageValidationDispatcher(ContestPackageRepository packages, JudgeQueue queue) {
        this.packages = packages; this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        for (var pkg : packages.findTop50ByValidationStateInOrderByUpdatedAtAsc(List.of("PENDING"))) {
            try { queue.publishOnce(JudgeQueues.TESTCASE_GEN, "package:" + pkg.getDigest(), 604800); pkg.markValidating(); packages.save(pkg); }
            catch (RuntimeException ignored) { }
        }
    }
}
