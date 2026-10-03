package com.xauat.oj.infrastructure.judge;

import java.util.Arrays;

/**
 * 输出比较策略，保持旧后端支持的 checker 类型：text / exact / tokens / float。
 * custom 需要沙箱自定义程序，当前环境不支持，由调用方判定为 SystemError。
 */
public final class OutputChecker {
    private OutputChecker() {}

    public static boolean supported(String checker) {
        return checker != null && switch (checker) {
            case "exact", "text", "tokens", "float" -> true;
            default -> false;
        };
    }

    public static boolean matches(String checker, String actual, String expected) {
        return matches(checker, actual, expected, 1e-6, 1e-6);
    }

    public static boolean matches(String checker, String actual, String expected, double absoluteTolerance, double relativeTolerance) {
        String normalized = checker == null ? "text" : checker.toLowerCase();
        String left = actual == null ? "" : actual;
        String right = expected == null ? "" : expected;
        return switch (normalized) {
            case "tokens" -> Arrays.equals(left.trim().split("\\s+"), right.trim().split("\\s+"));
            case "float" -> matchesFloat(left, right, absoluteTolerance, relativeTolerance);
            default -> left.trim().equals(right.trim());
        };
    }

    private static boolean matchesFloat(String actual, String expected, double absolute, double relative) {
        String[] a = actual.trim().split("\\s+");
        String[] e = expected.trim().split("\\s+");
        if (a.length != e.length) return false;
        try {
            for (int i = 0; i < a.length; i++) {
                double x = Double.parseDouble(a[i]);
                double y = Double.parseDouble(e[i]);
                if (!Double.isFinite(x) || !Double.isFinite(y)) return false;
                if (Math.abs(x - y) > Math.max(absolute, relative * Math.abs(y))) return false;
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
