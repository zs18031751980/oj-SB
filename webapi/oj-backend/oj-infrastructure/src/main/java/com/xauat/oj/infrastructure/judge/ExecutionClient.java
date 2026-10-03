package com.xauat.oj.infrastructure.judge;

/**
 * 判题执行后端抽象：生产为 Judge0，本地开发可为无隔离的 LocalCodeRunner。
 * 统一返回 {@link Judge0Client.Result}，便于判题编排共用一套逻辑。
 */
public interface ExecutionClient {
    Judge0Client.Result run(String sourceCode, String language, String stdin, int pollAttempts);
}
