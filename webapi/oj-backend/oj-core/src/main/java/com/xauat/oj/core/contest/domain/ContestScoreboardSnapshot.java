package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_scoreboard_snapshots", uniqueConstraints = @UniqueConstraint(name = "uq_scoreboard_snapshot_kind", columnNames = {"contest_id", "snapshot_kind"}))
public class ContestScoreboardSnapshot extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @Column(name = "snapshot_kind", nullable = false, length = 20) private String snapshotKind;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "scoreboard_version") private int scoreboardVersion;
    @Column(name = "event_cursor") private long eventCursor;
    protected ContestScoreboardSnapshot() {}
    public static ContestScoreboardSnapshot create(Contest contest, String kind, String payload, int version) { ContestScoreboardSnapshot item = new ContestScoreboardSnapshot(); item.contest = contest; item.snapshotKind = kind; item.payload = payload; item.scoreboardVersion = version; return item; }
    public void replace(String payload, int version) { this.payload = payload; this.scoreboardVersion = version; }
    public Integer getContestId() { return contest.getId(); }
    public String getSnapshotKind() { return snapshotKind; }
    public String getPayload() { return payload; }
    public int getScoreboardVersion() { return scoreboardVersion; }
}
