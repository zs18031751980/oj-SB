package com.xauat.oj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 未启用判题时保留任务不 ACK，避免任务被错误地标记完成。 */
@Component
@ConditionalOnProperty(name = "oj.judge.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledJudgeExecutor implements JudgeExecutor {
    @Override
    public void execute(String jobId) {
        throw new IllegalStateException("Judge executor is disabled; task remains unacknowledged");
    }
}
