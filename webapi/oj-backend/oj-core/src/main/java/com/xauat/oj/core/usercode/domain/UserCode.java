package com.xauat.oj.core.usercode.domain;

import com.xauat.oj.core.domain.BaseEntity;
import com.xauat.oj.core.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_codes", uniqueConstraints = @UniqueConstraint(name = "uq_user_codes_user_problem_language", columnNames = {"user_id", "problem_id", "language"}))
public class UserCode extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "problem_id", nullable = false)
    private Integer problemId;
    @Column(nullable = false, length = 50)
    private String language;
    @Column(nullable = false, columnDefinition = "text")
    private String code;

    protected UserCode() {}
    public static UserCode of(User user, Integer problemId, String language, String code) { UserCode item = new UserCode(); item.user = user; item.problemId = problemId; item.language = language; item.code = code; return item; }
    public Integer getProblemId() { return problemId; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
