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

    /**
     * 定时派发待验证题包到 testcase_gen_queue，成功后标记为 VALIDATING。
     * 投递失败不抛出，等待下一轮重试。
     */
    @Scheduled(fixedDelayString = "${oj.outbox.poll-delay-ms:1000}")
    @Transactional
    public void dispatch() {
        // 每次最多取 50 个最久未更新的 PENDING 题包，按更新时间升序保证公平
        for (var pkg : packages.findTop50ByValidationStateInOrderByUpdatedAtAsc(List.of("PENDING"))) {
            try { queue.publishOnce(JudgeQueues.TESTCASE_GEN, "package:" + pkg.getDigest(), 604800); pkg.markValidating(); packages.save(pkg); }
            catch (RuntimeException ignored) { }
        }
    }
}
