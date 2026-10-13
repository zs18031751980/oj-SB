package com.xauat.oj.core.contest.domain;

import com.xauat.oj.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 比赛题目实体，对应数据库表 {@code contest_problems}。
 *
 * <p>比赛中的一道题目，包含题面、样例、时空限制与判题配置等；题包通过
 * {@code packageDigest} 关联，校验状态 {@code validationStatus} 取值：
 * PENDING（待校验）、VALID（有效）、INVALID（无效）。</p>
 */
@Entity
@Table(name = "contest_problems")
public class ContestProblem extends BaseEntity {
    /** 所属比赛。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    /** 题目序号（如 A、B、C），比赛内展示用。 */
    @Column(name = "problem_index", nullable = false, length = 10)
    private String problemIndex;
    /** 题目标题。 */
    @Column(nullable = false, length = 200)
    private String title;
    /** 题面描述。 */
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    /** 输入格式说明，默认空串。 */
    @Column(name = "input_desc", columnDefinition = "text")
    private String inputDesc = "";
    /** 输出格式说明，默认空串。 */
    @Column(name = "output_desc", columnDefinition = "text")
    private String outputDesc = "";
    /** 参考答案/标准答案。 */
    @Column(name = "correct_answer", nullable = false, columnDefinition = "text")
    private String correctAnswer;
    /** 时间限制（毫秒），默认 1000。 */
    @Column(name = "time_limit")
    private int timeLimit = 1000;
    /** 内存限制（MB），默认 256。 */
    @Column(name = "memory_limit")
    private int memoryLimit = 256;
    /** 难度，默认“中等”。 */
    @Column(length = 20)
    private String difficulty = "中等";
    /** 默认语言，默认 cpp。 */
    @Column(length = 20)
    private String language = "cpp";
    /** 样例列表（JSON 数组文本），默认 []。 */
    @Column(columnDefinition = "text")
    private String samples = "[]";
    /** 题目分值，默认 100。 */
    private int score = 100;
    /** 排序序号，控制比赛内题目顺序。 */
    @Column(name = "sort_order")
    private int sortOrder;
    /** 校验版本号，题包变更时递增；默认 1。 */
    @Column(name = "validation_version")
    private int validationVersion = 1;
    /** 校验状态，默认 PENDING。 */
    @Column(name = "validation_status", length = 20)
    private String validationStatus = "PENDING";
    /** 校验错误信息。 */
    @Column(name = "validation_error", columnDefinition = "text")
    private String validationError;
    /** 判题器配置（JSON 文本），默认 text 全文比较。 */
    @Column(name = "checker_config", columnDefinition = "text")
    private String checkerConfig = "{\"checker\":\"text\"}";
    /** 当前生效题包的内容摘要。 */
    @Column(name = "package_digest", length = 64)
    private String packageDigest;
    protected ContestProblem() {
    }
    /** 创建比赛题目，对空值字段填充默认值。 */
    public static ContestProblem create(Contest contest, String problemIndex, String title, String description,
            String inputDesc, String outputDesc, String correctAnswer, int timeLimit,
            int memoryLimit, String difficulty, String language, String samples, int sortOrder) {
        ContestProblem item = new ContestProblem();
        item.contest = contest;
        item.problemIndex = problemIndex;
        item.title = title;
        item.description = description;
        item.inputDesc = inputDesc == null ? "" : inputDesc;
        item.outputDesc = outputDesc == null ? "" : outputDesc;
        item.correctAnswer = correctAnswer;
        item.timeLimit = timeLimit;
        item.memoryLimit = memoryLimit;
        item.difficulty = difficulty == null ? "中等" : difficulty;
        item.language = language == null ? "cpp" : language;
        item.samples = samples == null || samples.isBlank() ? "[]" : samples;
        item.sortOrder = sortOrder;
        return item;
    }
    /** 更新题目内容与限制；null 参数表示保持原值不变。 */
    public void updateAsset(String problemIndex, String title, String description, String inputDesc, String outputDesc,
            String correctAnswer, Integer timeLimit, Integer memoryLimit, String difficulty,
            String language, String samples, Integer sortOrder) {
        if (problemIndex != null && !problemIndex.isBlank())
            this.problemIndex = problemIndex;
        if (title != null && !title.isBlank())
            this.title = title;
        if (description != null)
            this.description = description;
        if (inputDesc != null)
            this.inputDesc = inputDesc;
        if (outputDesc != null)
            this.outputDesc = outputDesc;
        if (correctAnswer != null)
            this.correctAnswer = correctAnswer;
        if (timeLimit != null)
            this.timeLimit = timeLimit;
        if (memoryLimit != null)
            this.memoryLimit = memoryLimit;
        if (difficulty != null)
            this.difficulty = difficulty;
        if (language != null && !language.isBlank())
            this.language = language;
        if (samples != null)
            this.samples = samples;
        if (sortOrder != null)
            this.sortOrder = sortOrder;
    }
    /** 触发重新校验：递增校验版本并重置为待校验。 */
    public void bumpValidation() {
        this.validationVersion++;
        this.validationStatus = "PENDING";
        this.validationError = null;
    }
    public Integer getId() {
        return super.getId();
    }
    public String getProblemIndex() {
        return problemIndex;
    }
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }
    public String getInputDesc() {
        return inputDesc == null ? "" : inputDesc;
    }
    public String getOutputDesc() {
        return outputDesc == null ? "" : outputDesc;
    }
    public int getTimeLimit() {
        return timeLimit;
    }
    public int getMemoryLimit() {
        return memoryLimit;
    }
    public String getDifficulty() {
        return difficulty;
    }
    public String getSamples() {
        return samples == null ? "[]" : samples;
    }
    public Integer getContestId() {
        return contest == null ? null : contest.getId();
    }
    public String getCheckerConfig() {
        return checkerConfig;
    }
    public String getPackageDigest() {
        return packageDigest;
    }
    public int getValidationVersion() {
        return validationVersion;
    }
    public String getValidationStatus() {
        return validationStatus;
    }
    public String getValidationError() {
        return validationError;
    }
    public int getSortOrder() {
        return sortOrder;
    }
    public int getScore() {
        return score;
    }
    /** 仅关联题包摘要，不改变题目内容。 */
    public void attachPackage(String digest) {
        this.packageDigest = digest;
    }
    /** 激活题包：写入摘要、限制、判题配置与标准答案，并置为有效状态。 */
    public void activatePackage(String digest, int timeLimit, int memoryLimit, String checkerConfig,
            String correctAnswer, String language) {
        this.packageDigest = digest;
        this.timeLimit = timeLimit;
        this.memoryLimit = memoryLimit;
        this.checkerConfig = checkerConfig == null || checkerConfig.isBlank()
                ? "{\"checker\":\"text\"}"
                : checkerConfig;
        this.correctAnswer = correctAnswer;
        this.language = language == null ? "cpp" : language;
        this.validationStatus = "VALID";
        this.validationError = null;
    }
    /** 标记校验通过。 */
    public void markValidated() {
        this.validationStatus = "VALID";
        this.validationError = null;
    }
    /** 标记校验失败，错误信息截断至 1000 字符。 */
    public void markValidationFailed(String error) {
        this.validationStatus = "INVALID";
        this.validationError = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error);
    }
    public String getCorrectAnswer() {
        return correctAnswer;
    }
    public String getLanguage() {
        return language;
    }
    /** 更新判题器配置；为空时回落为默认 text 比较器。 */
    public void updateCheckerConfig(String checkerConfig) {
        this.checkerConfig = checkerConfig == null || checkerConfig.isBlank()
                ? "{\"checker\":\"text\"}"
                : checkerConfig;
    }
}
