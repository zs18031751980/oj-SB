package com.xauat.oj.api.contest;

/**
 * 比赛可见性策略（与比赛详情、题面接口共用）。
 *
 * <p>managers/staff 与比赛内 jury 可读全部状态；草稿（DRAFT）、就绪（READY）、已取消（CANCELLED）
 * 对其他用户不可见；私有比赛仅参赛者可读；公开且已发布的比赛允许匿名读取。</p>
 */
final class ContestVisibility {
    private ContestVisibility() {
    }

    /**
     * 判断请求者能否查看比赛及其题面。
     *
     * @param lifecycle      生命周期状态
     * @param publicContest  是否公开比赛
     * @param privileged     是否 manager/staff
     * @param jury           是否具备比赛内 jury 能力
     * @param participant    是否为参赛者
     * @return 是否可见
     */
    static boolean canView(String lifecycle, boolean publicContest, boolean privileged, boolean jury,
            boolean participant) {
        if (privileged || jury)
            return true;
        if ("DRAFT".equals(lifecycle) || "READY".equals(lifecycle) || "CANCELLED".equals(lifecycle))
            return false;
        if (!publicContest)
            return participant;
        return true;
    }
}
