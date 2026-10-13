package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 比赛实体，对应数据库表 {@code contests}。
 *
 * <p>描述一场在线评测比赛的基本信息、赛制、时间与生命周期。
 * 生命周期 {@code lifecycleState} 取值：DRAFT（草稿）、READY（就绪）、SCHEDULED（已排期/发布）、
 * RUNNING（进行中）、FROZEN（封榜）、FINALIZED（已结算）、CANCELLED（已取消）。</p>
 *
 * <p>业务状态 {@code status} 取值示例：upcoming（未开始）、past（已结束）等，
 * 主要用于对外展示，与生命周期状态配合使用。</p>
 */
@Entity
@Table(name = "contests")
public class Contest extends BaseEntity {
    /** 比赛标题，最长 200 字符。 */
    @Column(nullable = false, length = 200) private String title;
    /** 比赛说明（Markdown/纯文本），默认空串。 */
    @Column(columnDefinition = "text") private String description = "";
    /** 赛制类型，如 ACM、OI 等；默认 ACM。 */
    @Column(name = "contest_type", length = 50) private String contestType = "ACM";
    /** 对外展示状态，默认 upcoming。 */
    @Column(length = 20) private String status = "upcoming";
    /** 比赛开始时间。 */
    @Column(name = "start_time") private LocalDateTime startTime;
    /** 比赛结束时间。 */
    @Column(name = "end_time") private LocalDateTime endTime;
    /** 创建者用户 ID。 */
    @Column(name = "created_by") private Integer createdBy;
    /** 是否公开；false 时为私有比赛。 */
    @Column(name = "is_public") private boolean publicContest = true;
    /** 罚时（分钟），用于 ACM 排名计算；默认 20。 */
    @Column(name = "penalty_time") private int penaltyTime = 20;
    /** 生命周期状态，默认 DRAFT。 */
    @Column(name = "lifecycle_state", length = 20) private String lifecycleState = "DRAFT";
    /** 封榜时间；到达后排行榜冻结。 */
    @Column(name = "freeze_time") private LocalDateTime freezeTime;
    /** 发布时间。 */
    @Column(name = "published_at") private LocalDateTime publishedAt;
    /** 结算时间。 */
    @Column(name = "finalized_at") private LocalDateTime finalizedAt;
    /** 解冻（揭榜）时间。 */
    @Column(name = "thawed_at") private LocalDateTime thawedAt;
    /** 最终排名版本号，结算或重算时递增。 */
    @Column(name = "final_revision") private int finalRevision;
    /** 规则版本标识，用于区分不同评测规则；默认 acm-2026-v1。 */
    @Column(name = "rules_version") private String rulesVersion = "acm-2026-v1";
    /** 允许使用的语言，JSON 数组文本；默认含 cpp/python/java/go/javascript。 */
    @Column(name = "allowed_languages", columnDefinition = "text") private String allowedLanguages = "[\"cpp\",\"python\",\"java\",\"go\",\"javascript\"]";
    /** 同一用户同时进行中的提交数上限；默认 3。 */
    @Column(name = "active_submission_limit") private int activeSubmissionLimit = 3;
    /** 排行榜刷新请求版本号，递增以触发前端重算。 */
    @Column(name = "scoreboard_requested_version") private int scoreboardRequestedVersion;
    protected Contest() {}
    /** 创建比赛（基础字段），其余使用默认值。 */
    public static Contest create(String title, String description, String type, LocalDateTime start, LocalDateTime end, Integer creator) { Contest item = new Contest(); item.title = title; item.description = description; item.contestType = type; item.startTime = start; item.endTime = end; item.createdBy = creator; return item; }
    /** 创建比赛并补充罚时、封榜时间与对外状态。 */
    public static Contest createFull(String title, String description, String type, LocalDateTime start, LocalDateTime end, Integer creator,
                                     int penaltyTime, LocalDateTime freezeTime, String status) {
        Contest item = create(title, description, type, start, end, creator);
        item.penaltyTime = penaltyTime; item.freezeTime = freezeTime; item.status = status;
        return item;
    }
    /** 仅 DRAFT/READY 可改；发布后冻结规则与时间。 */
    public void updateConfig(String title, String description, String type, LocalDateTime start, LocalDateTime end,
                             LocalDateTime freezeTime, boolean freezeProvided, Integer penaltyTime, String status) {
        if (!"DRAFT".equals(lifecycleState) && !"READY".equals(lifecycleState)) {
            throw new IllegalStateException("比赛发布后不能直接修改规则或时间");
        }
        if (title != null && !title.isBlank()) this.title = title;
        if (description != null && !description.isBlank()) this.description = description;
        if (type != null && !type.isBlank()) this.contestType = type;
        if (start != null) this.startTime = start;
        if (end != null) this.endTime = end;
        if (freezeProvided) this.freezeTime = freezeTime;
        if (penaltyTime != null && penaltyTime >= 0) this.penaltyTime = penaltyTime;
        if (status != null && !status.isBlank()) this.status = status;
    }
    public java.time.LocalDateTime getThawedAt() { return thawedAt; }
    public boolean isPublic() { return publicContest; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContestType() { return contestType; }
    public String getLifecycleState() { return lifecycleState; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isPublicContest() { return publicContest; }
    public int getPenaltyTime() { return penaltyTime; }
    public LocalDateTime getFreezeTime() { return freezeTime; }
    public String getRulesVersion() { return rulesVersion; }
    public String getAllowedLanguages() { return allowedLanguages; }
    public int getActiveSubmissionLimit() { return activeSubmissionLimit; }
    public int getScoreboardRequestedVersion() { return scoreboardRequestedVersion; }
    public int getFinalRevision() { return finalRevision; }
    /** 更新规则版本、允许语言、罚时、并发提交上限与封榜时间；空值表示不修改，封榜时间总是覆盖。 */
    public void updateRules(String rulesVersion, String allowedLanguages, Integer penaltyTime, Integer activeSubmissionLimit, LocalDateTime freezeTime) {
        if (rulesVersion != null && !rulesVersion.isBlank()) this.rulesVersion = rulesVersion;
        if (allowedLanguages != null && !allowedLanguages.isBlank()) this.allowedLanguages = allowedLanguages;
        if (penaltyTime != null && penaltyTime >= 0) this.penaltyTime = penaltyTime;
        if (activeSubmissionLimit != null && activeSubmissionLimit > 0) this.activeSubmissionLimit = activeSubmissionLimit;
        this.freezeTime = freezeTime;
    }
    /** 请求刷新排行榜：递增请求版本号。 */
    public void requestScoreboardRefresh() { scoreboardRequestedVersion++; }
    /** 发布比赛：仅 DRAFT 可发布，发布后进入 SCHEDULED 并记录发布时间。 */
    public void publish() { if (!"DRAFT".equals(lifecycleState)) throw new IllegalStateException("比赛当前状态不能发布"); lifecycleState = "SCHEDULED"; status = "upcoming"; publishedAt = LocalDateTime.now(); }
    /** 取消比赛：已结算的比赛不可取消。 */
    public void cancel() { if ("FINALIZED".equals(lifecycleState)) throw new IllegalStateException("比赛已结算"); lifecycleState = "CANCELLED"; status = "past"; }
    /** 编辑比赛详情：仅草稿状态可编辑基础信息与时间。 */
    public void updateDetails(String title, String description, String type, LocalDateTime start, LocalDateTime end) {
        if (!"DRAFT".equals(lifecycleState)) throw new IllegalStateException("只有草稿比赛可以编辑");
        this.title = title; this.description = description; this.contestType = type; this.startTime = start; this.endTime = end;
    }
    /** 解冻（揭榜）：将封榜的比赛恢复为进行中并记录解冻时间。 */
    public void thaw() { if (!"FROZEN".equals(lifecycleState) && freezeTime == null) throw new IllegalStateException("比赛当前未冻结"); thawedAt = LocalDateTime.now(); lifecycleState = "RUNNING"; }
    /** 结算比赛：已取消的比赛不可结算，结算后写入结束状态与时间。 */
    public void finalizeContest() { if ("CANCELLED".equals(lifecycleState)) throw new IllegalStateException("已取消的比赛不能结算"); finalizedAt = LocalDateTime.now(); lifecycleState = "FINALIZED"; status = "past"; }
    /** 递增最终排名版本号，标记榜单需重新发布。 */
    public void bumpFinalRevision() { finalRevision++; }
}
