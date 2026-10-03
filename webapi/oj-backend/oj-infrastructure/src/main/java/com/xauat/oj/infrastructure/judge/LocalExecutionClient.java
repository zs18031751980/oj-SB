package com.xauat.oj.infrastructure.judge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地开发执行后端：用 {@link LocalCodeRunner} 直接在主机制译/运行，映射为 Judge0 状态码，
 * 使判题编排在无 Docker/Judge0 时也能工作。仅在 oj.judge.executor=local 时启用，严禁生产使用。
 */
@Component
@ConditionalOnProperty(name = "oj.judge.executor", havingValue = "local")
public class LocalExecutionClient implements ExecutionClient {
    private final LocalCodeRunner runner;

    public LocalExecutionClient(LocalCodeRunner runner) { this.runner = runner; }

    @Override
    public Judge0Client.Result run(String sourceCode, String language, String stdin, int pollAttempts) {
        LocalCodeRunner.Result result = runner.run(sourceCode, language, stdin, 10);
        if (result.timedOut()) {
            return new Judge0Client.Result(5, "Time Limit Exceeded", result.stdout(), result.stderr(), (int) result.timeMs(), 0, "", null);
        }
        Integer exit = result.exitCode();
        if (exit == null) {
            return new Judge0Client.Result(13, "Internal Error", result.stdout(), result.stderr(), (int) result.timeMs(), 0, result.message(), null);
        }
        if (exit == 0) {
            return new Judge0Client.Result(3, "Accepted", result.stdout(), result.stderr(), (int) result.timeMs(), 0, "", 0);
        }
        if (result.stderr().isBlank() && !result.message().isBlank()) {
            return new Judge0Client.Result(6, "Compilation Error", result.stdout(), "", (int) result.timeMs(), 0, result.message(), exit);
        }
        return new Judge0Client.Result(11, "Runtime Error", result.stdout(), result.stderr(), (int) result.timeMs(), 0, "", exit);
    }
}
