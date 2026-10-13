package com.xauat.oj.api.contest;

import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestScoreboardSnapshotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 比赛榜单后台投影：当 Contest.scoreboard_requested_version 前进或快照缺失时重建，
 * API 读取快照而非实时全量扫描。
 */
@Component
public class ScoreboardProjection {
    private final ContestRepository contests;
    private final ContestScoreboardSnapshotRepository snapshots;
    private final ContestScoreboardService scoreboard;

    public ScoreboardProjection(ContestRepository contests, ContestScoreboardSnapshotRepository snapshots,
            ContestScoreboardService scoreboard) {
        this.contests = contests;
        this.snapshots = snapshots;
        this.scoreboard = scoreboard;
    }

    /**
     * 定时重建榜单快照：LIVE 版本落后则重建；封榜后补建 PUBLIC_FREEZE 快照；
     * 已结算比赛补建 FINAL 快照。已发布且非取消的比赛才会被投影。
     */
    @Scheduled(fixedDelayString = "${oj.scoreboard.refresh-ms:10000}",
            initialDelayString = "${oj.scoreboard.initial-delay-ms:5000}")
    public void refresh() {
        for (Contest contest : contests.findAll()) {
            String state = contest.getLifecycleState();
            // 草稿/就绪/取消的比赛不生成榜单
            if (state == null || state.equals("DRAFT") || state.equals("READY") || state.equals("CANCELLED"))
                continue;
            // 请求版本落后于当前快照版本，或快照缺失时重建 LIVE
            boolean changed = snapshots.findByContest_IdAndSnapshotKind(contest.getId(), ContestScoreboardService.LIVE)
                    .map(s -> s.getScoreboardVersion() < contest.getScoreboardRequestedVersion())
                    .orElse(true);
            if (changed)
                scoreboard.ensure(contest, ContestScoreboardService.LIVE);
            // 到达封榜时间且尚无冻结快照时补建，供非裁判用户查看
            if (contest.getFreezeTime() != null && LocalDateTime.now().isAfter(contest.getFreezeTime())
                    && snapshots
                            .findByContest_IdAndSnapshotKind(contest.getId(), ContestScoreboardService.PUBLIC_FREEZE)
                            .isEmpty()) {
                scoreboard.ensure(contest, ContestScoreboardService.PUBLIC_FREEZE);
            }
            // 已结算比赛生成不可变的最终快照
            if ("FINALIZED".equals(state)) {
                String finalKind = scoreboard.kindFor(contest, false);
                if (snapshots.findByContest_IdAndSnapshotKind(contest.getId(), finalKind).isEmpty()) {
                    scoreboard.ensure(contest, finalKind);
                }
            }
        }
    }
}
