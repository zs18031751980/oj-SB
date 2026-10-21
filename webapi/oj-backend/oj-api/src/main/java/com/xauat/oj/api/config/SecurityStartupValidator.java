package com.xauat.oj.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动期安全自检：拒绝以不安全的默认配置启动。
 *
 * <p>该检查发生在应用对外提供服务之前，命中以下任一情况会直接终止启动：
 * 使用仓库内置的 JWT 占位密钥、JWT 密钥过短、使用占位指标令牌；
 * 生产环境启用无沙箱本机执行或仍选择 {@code local} 执行器；
 * 生产环境未配置指标令牌且未显式允许匿名。</p>
 */
@Component
public class SecurityStartupValidator {
    /** 仓库中的占位 JWT 密钥，绝不允许用于任何环境。 */
    private static final String DEFAULT_JWT_SECRET = "change-this-secret-in-production-change-this-secret-in-production";
    /** 仓库中的占位指标令牌。 */
    private static final String DEFAULT_METRICS_TOKEN = "change-me-metrics-token";

    private final String environment;
    private final String executor;
    private final String jwtSecret;
    private final String metricsToken;
    private final boolean localJudgeEnabled;
    private final boolean metricsAllowAnonymous;

    public SecurityStartupValidator(
            @Value("${oj.environment:development}") String environment,
            @Value("${oj.judge.executor:judge0}") String executor,
            @Value("${oj.auth.jwt-secret:}") String jwtSecret,
            @Value("${oj.metrics.token:}") String metricsToken,
            @Value("${oj.judge.local-enabled:false}") boolean localJudgeEnabled,
            @Value("${oj.metrics.allow-anonymous:false}") boolean metricsAllowAnonymous) {
        this.environment = environment;
        this.executor = executor;
        this.jwtSecret = jwtSecret;
        this.metricsToken = metricsToken;
        this.localJudgeEnabled = localJudgeEnabled;
        this.metricsAllowAnonymous = metricsAllowAnonymous;
    }

    @PostConstruct
    public void validate() {
        boolean production = "production".equalsIgnoreCase(environment);
        // 占位/弱密钥在所有环境都拒绝
        if (jwtSecret == null || jwtSecret.isBlank())
            throw new IllegalStateException("OJ_JWT_SECRET 未配置");
        if (DEFAULT_JWT_SECRET.equals(jwtSecret) || jwtSecret.length() < 32)
            throw new IllegalStateException("OJ_JWT_SECRET 使用了占位值或长度不足 32，请更换为随机密钥");
        if (DEFAULT_METRICS_TOKEN.equals(metricsToken))
            throw new IllegalStateException("METRICS_TOKEN 使用了占位值，请更换为随机令牌");

        if (!production)
            return;

        // 生产环境的硬性要求：禁止无沙箱本机执行
        if (localJudgeEnabled || "local".equalsIgnoreCase(executor))
            throw new IllegalStateException("生产环境禁止使用 local 执行器（OJ_ALLOW_LOCAL_JUDGE/OJ_JUDGE_EXECUTOR）");
        if ((metricsToken == null || metricsToken.isBlank()) && !metricsAllowAnonymous)
            throw new IllegalStateException("生产环境必须配置 METRICS_TOKEN（或显式 OJ_METRICS_ALLOW_ANONYMOUS=true）");
    }
}
