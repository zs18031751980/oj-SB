package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 判题记录实体，对应数据库表 {@code judgements}。
 *
 * <p>按“提交 + 尝试次数”保存每一次判题过程与结果原始载荷，形成可追溯的判题历史；
 * 重判会产生新的 attempt 记录，{@code batchId} 关联所属重判批次。</p>
 */
@Entity
@Table(name = "judgements")
public class Judgement extends BaseEntity {
    /** 关联的提交。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id") private ContestSubmission submission;
    /** 对应的提交尝试次数。 */
    @Column(name = "attempt_id", nullable = false) private int attemptId;
    /** 判题状态/结论（如 AC、WA 等）。 */
    @Column(nullable = false, length = 32) private String status;
    /** 判题原始结果载荷（JSON 文本）。 */
    @Column(nullable = false, columnDefinition = "text") private String payload;
    /** 本次判题使用的题包摘要。 */
    @Column(name = "package_digest", length = 64) private String packageDigest;
    /** 所属重判批次 ID（普通判题为空）。 */
    @Column(name = "batch_id") private Integer batchId;
    protected Judgement() {}
    /** 创建判题记录；payload 为空时置为 {}。 */
    public static Judgement of(ContestSubmission submission, int attemptId, String status, String payload) { Judgement item = new Judgement(); item.submission = submission; item.attemptId = attemptId; item.status = status; item.payload = payload == null ? "{}" : payload; return item; }
    /** 创建判题记录并附加题包摘要与重判批次信息。 */
    public static Judgement of(ContestSubmission submission, int attemptId, String status, String payload, String packageDigest, Integer batchId) { Judgement item = of(submission, attemptId, status, payload); item.packageDigest = packageDigest; item.batchId = batchId; return item; }
    /** 返回尝试次数。 */
    public int getAttemptId() { return attemptId; }
}
