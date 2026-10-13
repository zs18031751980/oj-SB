package com.xauat.oj.api.config;

import com.xauat.oj.api.web.RequestIdFilter;
import com.xauat.oj.api.web.CsrfProtectionFilter;
import com.xauat.oj.api.web.OriginValidationFilter;
import com.xauat.oj.api.web.SecurityHeadersFilter;
import com.xauat.oj.api.web.MetricsAuthFilter;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Web 基础设施配置：静态资源映射、外层安全过滤器注册顺序、Tomcat 连接器与 CORS。
 *
 * <p>安全链路中的位置：将 {@code RequestId → CSRF → Origin 校验 → 安全响应头 → Metrics 认证}
 * 五个 Servlet 过滤器按确定顺序挂载到请求链最外层，先于 Spring Security 处理。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${oj.upload.avatar-dir:./data/avatars}") private String avatarDir;

    /**
     * 将本地头像目录映射到 {@code /uploads/avatars/**}。
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(avatarDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/avatars/**").addResourceLocations(location);
    }

    /** 注册请求 id 过滤器（order=0，全部路径），供日志与错误响应关联同一请求。 */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(0);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** 注册 CSRF 保护过滤器（order=1，仅 /auth/*），要求写操作携带 X-CSRF-Protection 头。 */
    @Bean
    FilterRegistrationBean<CsrfProtectionFilter> csrfProtectionFilter() {
        FilterRegistrationBean<CsrfProtectionFilter> registration = new FilterRegistrationBean<>(new CsrfProtectionFilter());
        registration.setOrder(1); registration.addUrlPatterns("/auth/*"); return registration;
    }

    /** 注册来源校验过滤器（order=2，全部路径），限制浏览器跨站写请求。 */
    @Bean
    FilterRegistrationBean<OriginValidationFilter> originValidationFilter(@Value("${oj.cors.origins:}") String origins) {
        FilterRegistrationBean<OriginValidationFilter> registration = new FilterRegistrationBean<>(new OriginValidationFilter(origins));
        registration.setOrder(2); registration.addUrlPatterns("/*"); return registration;
    }

    /** 注册安全响应头过滤器（order=3，全部路径）。 */
    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(3); registration.addUrlPatterns("/*"); return registration;
    }

    /** 注册指标端点认证过滤器（order=4，仅 /actuator/prometheus）。 */
    @Bean
    FilterRegistrationBean<MetricsAuthFilter> metricsAuthFilter(@Value("${oj.metrics.token:}") String token) {
        FilterRegistrationBean<MetricsAuthFilter> registration = new FilterRegistrationBean<>(new MetricsAuthFilter(token));
        registration.setOrder(4); registration.addUrlPatterns("/actuator/prometheus"); return registration;
    }

    /** 学习资源 id 含 `/`，前端以 %2F 传输；让 Tomcat 解码编码斜杠。 */
    @Bean
    TomcatConnectorCustomizer encodedSolidusConnectorCustomizer() {
        return connector -> connector.setEncodedSolidusHandling("decode");
    }

    /** 与旧后端一致：仅对白名单来源返回 CORS 头，支持携带 Cookie。 */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${oj.cors.origins:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        if (!allowed.isEmpty()) config.setAllowedOrigins(allowed);
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "Idempotency-Key", "X-CSRF-Protection"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
