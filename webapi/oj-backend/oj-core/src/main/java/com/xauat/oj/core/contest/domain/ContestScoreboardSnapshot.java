package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 比赛排行榜快照实体，对应数据库表 {@code contest_scoreboard_snapshots}。
 *
 * <p>缓存比赛榜单的序列化结果，避免每次请求实时重算；
 * {@code snapshotKind} 区分快照类型（如实时、封榜、最终），
 * 并通过 {@code (contest_id, snapshot_kind)} 唯一约束保证每种类型仅一份。</p>
 */
@Entity
@Table(name = "contest_scoreboard_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uq_scoreboard_snapshot_kind", columnNames = {
        "contest_id", "snapshot_kind"}))
public class ContestScoreboardSnapshot extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 快照类型标识（如 LIVE/FROZEN/FINAL）。 */
    @Column(name = "snapshot_kind", nullable = false, length = 20)
    private String snapshotKind;
    /** 榜单序列化内容（JSON 文本）。 */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    /** 对应的榜单版本号。 */
    @Column(name = "scoreboard_version")
    private int scoreboardVersion;
    /** 生成该快照时的事件游标，用于增量追平。 */
    @Column(name = "event_cursor")
    private long eventCursor;
    protected ContestScoreboardSnapshot() {
    }
    /** 创建榜单快照。 */
    public static ContestScoreboardSnapshot create(Contest contest, String kind, String payload, int version) {
        ContestScoreboardSnapshot item = new ContestScoreboardSnapshot();
        item.contest = contest;
        item.snapshotKind = kind;
        item.payload = payload;
        item.scoreboardVersion = version;
        return item;
    }
    /** 用新内容替换快照并更新版本号。 */
    public void replace(String payload, int version) {
        this.payload = payload;
        this.scoreboardVersion = version;
    }
    /** 返回比赛 ID。 */
    public Integer getContestId() {
        return contest.getId();
    }
    /** 返回快照类型。 */
    public String getSnapshotKind() {
        return snapshotKind;
    }
    /** 返回快照内容。 */
    public String getPayload() {
        return payload;
    }
    /** 返回榜单版本号。 */
    public int getScoreboardVersion() {
        return scoreboardVersion;
    }
}
