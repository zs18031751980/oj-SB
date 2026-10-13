package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

/**
 * 比赛问题澄清（提问/答疑）实体，对应数据库表 {@code contest_clarifications}。
 *
 * <p>参赛者可对题目发起提问，裁判可先认领（claimedBy）再作答（answeredBy），
 * 并决定答案是否广播给所有参赛者（broadcast）。</p>
 */
@Entity
@Table(name = "contest_clarifications")
public class ContestClarification extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    /** 提问者。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    /** 提问内容。 */
    @Column(nullable = false, columnDefinition = "text") private String question;
    /** 裁判回复内容；未回复时为 null。 */
    @Column(columnDefinition = "text") private String answer;
    /** 认领该问题的裁判用户 ID。 */
    @Column(name = "claimed_by") private Integer claimedBy;
    /** 实际作答的裁判用户 ID。 */
    @Column(name = "answered_by") private Integer answeredBy;
    /** 答案是否广播给全体参赛者。 */
    private boolean broadcast;
    protected ContestClarification() {}
    /** 参赛者发起提问。 */
    public static ContestClarification ask(Contest contest, User author, String question) { ContestClarification item = new ContestClarification(); item.contest = contest; item.author = author; item.question = question; return item; }
    /** 返回所属比赛 ID。 */
    public Integer getContestId() { return contest.getId(); }
    /** 返回主键 ID。 */
    public Integer getId() { return super.getId(); }
    /** 返回提问内容。 */
    public String getQuestion() { return question; }
    /** 返回回复内容。 */
    public String getAnswer() { return answer; }
    /** 返回认领人用户 ID。 */
    public Integer getClaimedBy() { return claimedBy; }
    /** 认领该澄清问题。 */
    public void setClaimedBy(Integer userId) { this.claimedBy = userId; }
    /** 裁判作答，记录作答人并设置是否广播。 */
    public void answer(Integer userId, String value, boolean broadcast) { this.answer = value; this.answeredBy = userId; this.broadcast = broadcast; }
}
