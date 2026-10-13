package com.xauat.oj.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OJ 后端 API 应用入口。
 *
 * <p>统一启用以下能力：
 * <ul>
 *   <li>{@code @SpringBootApplication(scanBasePackages = "com.xauat.oj")} 扫描全项目 Bean；</li>
 *   <li>{@code @EntityScan} / {@code @EnableJpaRepositories} 让 JPA 实体与仓储来自 {@code com.xauat.oj.core}；</li>
 *   <li>{@code @EnableScheduling} 开启定时任务（outbox 派发、排行榜投影等）。</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.xauat.oj")
@EntityScan("com.xauat.oj.core")
@EnableJpaRepositories("com.xauat.oj.core")
@EnableScheduling
public class OjApiApplication {
    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 学习资源 id 含 `/`，前端用 encodeURIComponent 编码为 %2F；Tomcat 默认拒绝编码斜杠。
        System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
        SpringApplication.run(OjApiApplication.class, args);
    }
}
