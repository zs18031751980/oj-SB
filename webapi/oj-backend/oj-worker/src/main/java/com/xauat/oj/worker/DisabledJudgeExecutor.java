package com.xauat.oj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(JudgeExecutor.class)
public class DisabledJudgeExecutor implements JudgeExecutor {
    @Override
    public void execute(String jobId) {
        throw new IllegalStateException("Judge executor is disabled; task remains unacknowledged");
    }
}
