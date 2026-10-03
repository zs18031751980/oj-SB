package com.xauat.oj.infrastructure.judge;

/**
 * Judge0 状态码到旧后端短状态码的映射，前端依赖这些短码（AC/WA/TLE/…）。
 */
public final class JudgeVerdict {
    public static final String ACCEPTED = "AC";
    public static final String WRONG_ANSWER = "WA";
    public static final String COMPILATION_ERROR = "CE";
    public static final String TIME_LIMIT_EXCEEDED = "TLE";
    public static final String MEMORY_LIMIT_EXCEEDED = "MLE";
    public static final String OUTPUT_LIMIT_EXCEEDED = "OLE";
    public static final String RUNTIME_ERROR = "RE";
    public static final String SEGMENTATION_FAULT = "SIGSEGV";
    public static final String SYSTEM_ERROR = "SystemError";
    public static final String PARTIAL = "Partial";

    private JudgeVerdict() {}

    public static boolean terminal(String status) {
        return switch (status == null ? "" : status) {
            case ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED,
                 OUTPUT_LIMIT_EXCEEDED, RUNTIME_ERROR, SEGMENTATION_FAULT, SYSTEM_ERROR, PARTIAL, "Cancelled", "SIGSYS" -> true;
            default -> false;
        };
    }

    public static String fromStatus(int statusId, String description) {
        return switch (statusId) {
            case 3 -> ACCEPTED;
            case 4 -> WRONG_ANSWER;
            case 5 -> TIME_LIMIT_EXCEEDED;
            case 6 -> COMPILATION_ERROR;
            case 7 -> SEGMENTATION_FAULT;
            case 8, 9, 10, 11, 12 -> RUNTIME_ERROR;
            default -> SYSTEM_ERROR;
        };
    }
}
