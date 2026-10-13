package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛参赛者实体，对应数据库表 {@code contest_participants}。
 *
 * <p>记录用户与比赛的参赛关系及当前积分/排名，通过 {@code (contest_id, user_id)}
 * 唯一约束保证同一用户在同一比赛中仅有一条记录。</p>
 */
@Entity
@Table(name = "contest_participants", uniqueConstraints = @UniqueConstraint(name = "uq_contest_participant", columnNames = {"contest_id", "user_id"}))
public class ContestParticipant extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    /** 参赛用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    /** 当前积分。 */
    private int score;
    /** 当前排名；未参与排名时为 null。 */
    private Integer rank;
    protected ContestParticipant() {}
    /** 建立参赛关系。 */
    public static ContestParticipant join(Contest contest, User user) { ContestParticipant item = new ContestParticipant(); item.contest = contest; item.user = user; return item; }
    /** 返回比赛 ID。 */
    public Integer getContestId() { return contest == null ? null : contest.getId(); }
    /** 返回用户 ID。 */
    public Integer getUserId() { return user == null ? null : user.getId(); }
    /** 返回积分。 */
    public int getScore() { return score; }
    /** 返回排名。 */
    public Integer getRank() { return rank; }
}
