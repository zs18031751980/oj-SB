package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛队伍实体，对应数据库表 {@code contest_teams}。
 *
 * <p>用于组队类比赛，记录队名与队长；队员关系见 {@link ContestTeamMember}。</p>
 */
@Entity
@Table(name = "contest_teams")
public class ContestTeam extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    /** 队长（创建者）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "captain_id") private User captain;
    /** 队伍名称，最长 120 字符。 */
    @Column(nullable = false, length = 120) private String name;
    protected ContestTeam() {}
    /** 创建队伍。 */
    public static ContestTeam create(Contest contest, User captain, String name) { ContestTeam item = new ContestTeam(); item.contest = contest; item.captain = captain; item.name = name; return item; }
    /** 返回比赛 ID。 */
    public Integer getContestId() { return contest.getId(); }
    /** 返回队伍名称。 */
    public String getName() { return name; }
    /** 返回队长用户 ID。 */
    public Integer getCaptainId() { return captain == null ? null : captain.getId(); }
    /** 返回队伍主键 ID。 */
    public Integer getId() { return super.getId(); }
}
