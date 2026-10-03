package com.xauat.oj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oj.judge.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledValidationExecutor implements ValidationExecutor {
    @Override
    public void execute(String jobId) {
        throw new IllegalStateException("Validation executor is disabled; task remains unacknowledged");
    }
}
