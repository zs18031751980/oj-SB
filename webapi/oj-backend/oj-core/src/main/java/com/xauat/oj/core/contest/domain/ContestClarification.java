package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "contest_clarifications")
public class ContestClarification extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contest_id") private Contest contest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, columnDefinition = "text") private String question;
    @Column(columnDefinition = "text") private String answer;
    @Column(name = "claimed_by") private Integer claimedBy;
    @Column(name = "answered_by") private Integer answeredBy;
    private boolean broadcast;
    protected ContestClarification() {}
    public static ContestClarification ask(Contest contest, User author, String question) { ContestClarification item = new ContestClarification(); item.contest = contest; item.author = author; item.question = question; return item; }
    public Integer getContestId() { return contest.getId(); }
    public Integer getId() { return super.getId(); }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public void answer(Integer userId, String value, boolean broadcast) { this.answer = value; this.answeredBy = userId; this.broadcast = broadcast; }
}
