package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛队伍成员实体，对应数据库表 {@code contest_team_members}。
 *
 * <p>记录用户所属的比赛队伍；通过 {@code (contest_id, user_id)} 唯一约束
 * 保证同一用户在同一比赛中只属于一个队伍。</p>
 */
@Entity
@Table(name = "contest_team_members", uniqueConstraints =
        @UniqueConstraint(name = "uq_contest_team_member", columnNames = {
        "contest_id", "user_id"}))
public class ContestTeamMember extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 所属队伍。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private ContestTeam team;
    /** 成员用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    protected ContestTeamMember() {
    }
    /** 将用户加入队伍的成员关系。 */
    public static ContestTeamMember create(Contest contest, ContestTeam team, User user) {
        ContestTeamMember item = new ContestTeamMember();
        item.contest = contest;
        item.team = team;
        item.user = user;
        return item;
    }
    /** 返回比赛 ID。 */
    public Integer getContestId() {
        return contest.getId();
    }
    /** 返回队伍 ID。 */
    public Integer getTeamId() {
        return team.getId();
    }
    /** 返回成员用户 ID。 */
    public Integer getUserId() {
        return user.getId();
    }
}
