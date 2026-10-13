package com.xauat.oj.core.contest.domain;

import jakarta.persistence.*;

/**
 * 比赛题包实体，对应数据库表 {@code contest_packages}。
 *
 * <p>保存某道比赛题的完整题包（含测试点、配置等序列化载荷），以内容摘要
 * {@code digest} 作为主键实现去重；题包需经过校验流水线，
 * {@code validationState} 取值：PENDING（待校验）、RUNNING（校验中）、VALID（有效）、INVALID（无效）。</p>
 */
@Entity
@Table(name = "contest_packages")
public class ContestPackage {
    /** 题包内容摘要，作为主键。 */
    @Id @Column(length = 64) private String digest;
    /** 所属比赛题目。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "problem_id") private ContestProblem problem;
    /** 题包序列化载荷（JSON 文本）。 */
    @Column(nullable = false, columnDefinition = "text") private String payload;
    /** 上传/操作人用户 ID。 */
    @Column(name = "actor_id") private Integer actorId;
    /** 校验状态，默认 VALID。 */
    @Column(name = "validation_state") private String validationState = "VALID";
    /** 校验失败原因。 */
    @Column(name = "validation_error", columnDefinition = "text") private String validationError;
    /** 创建时间。 */
    @Column(name = "created_at", nullable = false) private java.time.LocalDateTime createdAt;
    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false) private java.time.LocalDateTime updatedAt;
    protected ContestPackage() {}
    /** 创建一个校验状态为 VALID 的题包。 */
    public static ContestPackage create(String digest, ContestProblem problem, Integer actorId, String payload) { ContestPackage item = new ContestPackage(); item.digest = digest; item.problem = problem; item.actorId = actorId; item.payload = payload; item.createdAt = java.time.LocalDateTime.now(); item.updatedAt = item.createdAt; return item; }
    /** 创建一个待校验（PENDING）的题包。 */
    public static ContestPackage staged(String digest, ContestProblem problem, Integer actorId, String payload) { ContestPackage item = create(digest, problem, actorId, payload); item.validationState = "PENDING"; item.validationError = null; return item; }
    /** 返回题包摘要（主键）。 */
    public String getDigest() { return digest; }
    /** 返回所属题目 ID。 */
    public Integer getProblemId() { return problem == null ? null : problem.getId(); }
    /** 返回操作人 ID。 */
    public Integer getActorId() { return actorId; }
    /** 返回题包载荷。 */
    public String getPayload() { return payload; }
    /** 返回校验状态。 */
    public String getValidationState() { return validationState; }
    /** 返回校验错误信息。 */
    public String getValidationError() { return validationError; }
    /** 标记为校验中。 */
    public void markValidating() { this.validationState = "RUNNING"; this.updatedAt = java.time.LocalDateTime.now(); }
    /** 标记校验通过。 */
    public void markValid() { this.validationState = "VALID"; this.validationError = null; this.updatedAt = java.time.LocalDateTime.now(); }
    /** 标记校验失败，错误信息截断至 1000 字符。 */
    public void markInvalid(String error) { this.validationState = "INVALID"; this.validationError = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error); this.updatedAt = java.time.LocalDateTime.now(); }
    /** 替换题包载荷并重置为有效状态。 */
    public void replacePayload(String payload, Integer actorId) { this.payload = payload; this.actorId = actorId; this.validationState = "VALID"; this.validationError = null; this.updatedAt = java.time.LocalDateTime.now(); }
}
