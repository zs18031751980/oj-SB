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

/**
 * 用户代码草稿实体，对应数据库表 {@code user_codes}。
 *
 * <p>保存用户针对某题目、某语言的最新编辑代码；
 * 通过 {@code (user_id, problem_id, language)} 唯一约束保证每组仅保留一份草稿。</p>
 */
@Entity
@Table(name = "user_codes", uniqueConstraints =
        @UniqueConstraint(name = "uq_user_codes_user_problem_language", columnNames = {
        "user_id", "problem_id", "language"}))
public class UserCode extends BaseEntity {
    /** 代码所属用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    /** 题目 ID。 */
    @Column(name = "problem_id", nullable = false)
    private Integer problemId;
    /** 编程语言。 */
    @Column(nullable = false, length = 50)
    private String language;
    /** 代码内容。 */
    @Column(nullable = false, columnDefinition = "text")
    private String code;

    protected UserCode() {
    }
    /** 构建用户代码草稿。 */
    public static UserCode of(User user, Integer problemId, String language, String code) {
        UserCode item = new UserCode();
        item.user = user;
        item.problemId = problemId;
        item.language = language;
        item.code = code;
        return item;
    }
    /** 返回题目 ID。 */
    public Integer getProblemId() {
        return problemId;
    }
    /** 返回语言。 */
    public String getLanguage() {
        return language;
    }
    /** 返回代码内容。 */
    public String getCode() {
        return code;
    }
    /** 更新代码内容。 */
    public void setCode(String code) {
        this.code = code;
    }
}
