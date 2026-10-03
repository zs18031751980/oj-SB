package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "contests")
public class Contest extends BaseEntity {
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "text") private String description = "";
    @Column(name = "contest_type", length = 50) private String contestType = "ACM";
    @Column(length = 20) private String status = "upcoming";
    @Column(name = "start_time") private LocalDateTime startTime;
    @Column(name = "end_time") private LocalDateTime endTime;
    @Column(name = "created_by") private Integer createdBy;
    @Column(name = "is_public") private boolean publicContest = true;
    @Column(name = "penalty_time") private int penaltyTime = 20;
    @Column(name = "lifecycle_state", length = 20) private String lifecycleState = "DRAFT";
    @Column(name = "freeze_time") private LocalDateTime freezeTime;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "finalized_at") private LocalDateTime finalizedAt;
    @Column(name = "thawed_at") private LocalDateTime thawedAt;
    @Column(name = "final_revision") private int finalRevision;
    @Column(name = "rules_version") private String rulesVersion = "acm-2026-v1";
    @Column(name = "allowed_languages", columnDefinition = "text") private String allowedLanguages = "[\"cpp\",\"python\",\"java\",\"go\",\"javascript\"]";
    @Column(name = "active_submission_limit") private int activeSubmissionLimit = 3;
    @Column(name = "scoreboard_requested_version") private int scoreboardRequestedVersion;
    protected Contest() {}
    public static Contest create(String title, String description, String type, LocalDateTime start, LocalDateTime end, Integer creator) { Contest item = new Contest(); item.title = title; item.description = description; item.contestType = type; item.startTime = start; item.endTime = end; item.createdBy = creator; return item; }
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
    public void updateRules(String rulesVersion, String allowedLanguages, Integer penaltyTime, Integer activeSubmissionLimit, LocalDateTime freezeTime) {
        if (rulesVersion != null && !rulesVersion.isBlank()) this.rulesVersion = rulesVersion;
        if (allowedLanguages != null && !allowedLanguages.isBlank()) this.allowedLanguages = allowedLanguages;
        if (penaltyTime != null && penaltyTime >= 0) this.penaltyTime = penaltyTime;
        if (activeSubmissionLimit != null && activeSubmissionLimit > 0) this.activeSubmissionLimit = activeSubmissionLimit;
        this.freezeTime = freezeTime;
    }
    public void requestScoreboardRefresh() { scoreboardRequestedVersion++; }
    public void publish() { if (!"DRAFT".equals(lifecycleState)) throw new IllegalStateException("比赛当前状态不能发布"); lifecycleState = "SCHEDULED"; status = "upcoming"; publishedAt = LocalDateTime.now(); }
    public void cancel() { if ("FINALIZED".equals(lifecycleState)) throw new IllegalStateException("比赛已结算"); lifecycleState = "CANCELLED"; status = "past"; }
    public void updateDetails(String title, String description, String type, LocalDateTime start, LocalDateTime end) {
        if (!"DRAFT".equals(lifecycleState)) throw new IllegalStateException("只有草稿比赛可以编辑");
        this.title = title; this.description = description; this.contestType = type; this.startTime = start; this.endTime = end;
    }
    public void thaw() { if (!"FROZEN".equals(lifecycleState) && freezeTime == null) throw new IllegalStateException("比赛当前未冻结"); thawedAt = LocalDateTime.now(); lifecycleState = "RUNNING"; }
    public void finalizeContest() { if ("CANCELLED".equals(lifecycleState)) throw new IllegalStateException("已取消的比赛不能结算"); finalizedAt = LocalDateTime.now(); lifecycleState = "FINALIZED"; status = "past"; }
    public void bumpFinalRevision() { finalRevision++; }
}
