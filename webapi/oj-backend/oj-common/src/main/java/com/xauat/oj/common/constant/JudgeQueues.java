package com.xauat.oj.common.constant;

/**
 * 与旧后端保持一致的 Redis 队列名。这些名称属于外部契约，不得随意改名。
 */
public final class JudgeQueues {
    public static final String REGULAR = "judge_queue";
    public static final String CONTEST = "contest_judge_queue";
    public static final String PRACTICE = "practice_judge_queue";
    public static final String REJUDGE = "rejudge_queue";
    public static final String TESTCASE_GEN = "testcase_gen_queue";

    private JudgeQueues() {}
}
