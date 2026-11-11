package com.xauat.oj.api.contest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 比赛计分纯逻辑：ACM 罚时与 OI 最高分，不依赖数据库，便于单元测试与复用。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>ACM：首次 AC 计 1 题；罚时 = 首次 AC 用时（分钟）+ 此前错误提交次数 × 罚时系数；</li>
 *   <li>OI：取历次提交中的最高得分；不计罚时；</li>
 *   <li>已 AC 后不再因后续提交改变该题成绩；重复/迟到的结果不会让成绩回退。</li>
 * </ul>
 */
final class ContestScoring {
    private ContestScoring() {
    }

    /** 计算提交相对比赛开始的分钟数；任一时间为空返回 0。 */
    static int minutesBetween(LocalDateTime start, LocalDateTime submitted) {
        if (start == null || submitted == null)
            return 0;
        return (int) Duration.between(start, submitted).toMinutes();
    }

    /** 单题累计成绩。 */
    static final class ProblemScore {
        private final String index;
        private boolean solved;
        private int submissions;
        private int penalty;
        private int best;
        private int passed;
        private int total;
        private String status;
        private Integer solveMinutes;

        ProblemScore(String index) {
            this.index = index;
        }

        /**
         * 累计一次提交。
         *
         * @param verdict       判题结论（AC/WA/...），可为空
         * @param status        状态（兜底判定 AC）
         * @param passed        通过测试点数
         * @param total         测试点总数
         * @param score         显式得分（OI 可用），0 表示按通过比例计算
         * @param minutes       相对比赛开始的分钟数
         * @param acm           是否 ACM 赛制
         * @param penaltyMinutes 每次错误提交的罚时分钟
         */
        void accept(String verdict, String status, int passed, int total, int score, Integer minutes, boolean acm,
                int penaltyMinutes) {
            submissions++;
            // 通过用例数与总分取历史最大，避免部分结果回退覆盖已有成绩
            int pass = total == 0 ? 0 : Math.max(0, passed);
            int tot = Math.max(0, total);
            this.passed = Math.max(this.passed, pass);
            this.total = Math.max(this.total, tot);
            this.status = (verdict == null || verdict.isBlank()) ? status : verdict;
            int percent = tot == 0 ? 0 : (score > 0 ? score : pass * 100 / tot);
            // 已 AC 后不再改变该题成绩
            if (solved)
                return;
            boolean accepted = "AC".equalsIgnoreCase(verdict) || "Accepted".equalsIgnoreCase(verdict)
                    || "AC".equalsIgnoreCase(status);
            if (accepted) {
                solved = true;
                int usedMinutes = minutes == null ? 0 : minutes;
                solveMinutes = usedMinutes;
                // ACM 罚时 = 首次 AC 用时 + 此前错误提交次数 × 罚时系数；OI 不计罚时
                penalty = acm ? usedMinutes + (submissions - 1) * penaltyMinutes : 0;
            }
            best = Math.max(best, percent);
        }

        /** 该题是否通过（1/0）。 */
        int solvedCount() {
            return solved ? 1 : 0;
        }

        /** ACM 罚时分钟数。 */
        int penalty() {
            return penalty;
        }

        /** OI 得分。 */
        int oiScore() {
            return best;
        }

        /** 对外展示结构。 */
        Map<String, Object> asMap() {
            var map = new HashMap<String, Object>();
            map.put("problem_index", index);
            map.put("solved", solved);
            map.put("passed", passed);
            map.put("total", total);
            map.put("score", best);
            map.put("status", status == null ? "" : status);
            map.put("submissions", submissions);
            map.put("solve_minutes", solveMinutes);
            return map;
        }
    }
}
