package com.xauat.oj.worker;

/** 参考代码验证执行器；与用户提交执行分离，避免污染提交统计。 */
public interface ValidationExecutor {
    void execute(String jobId);
}
