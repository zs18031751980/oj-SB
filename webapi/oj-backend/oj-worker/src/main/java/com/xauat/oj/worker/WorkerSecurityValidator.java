package com.xauat.oj.worker;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Worker 启动期安全自检：生产环境禁止无沙箱的 {@code local} 执行器与占位 JWT 密钥。
 *
 * <p>与 API 侧校验形成双重防线，避免仅通过 Worker 配置绕过沙箱要求。</p>
 */
@Component
public class WorkerSecurityValidator {
    private static final String DEFAULT_JWT_SECRET = "change-this-secret-in-production-change-this-secret-in-production";

    private final String environment;
    private final String executor;
    private final String jwtSecret;

    public WorkerSecurityValidator(
            @Value("${oj.environment:development}") String environment,
            @Value("${oj.judge.executor:judge0}") String executor,
            @Value("${oj.auth.jwt-secret:}") String jwtSecret) {
        this.environment = environment;
        this.executor = executor;
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)
                || jwtSecret.length() < 32)
            throw new IllegalStateException("OJ_JWT_SECRET 使用了占位值或长度不足 32，请更换为随机密钥");
        if ("production".equalsIgnoreCase(environment) && "local".equalsIgnoreCase(executor))
            throw new IllegalStateException("生产环境禁止 Worker 使用 local 执行器");
    }
}
