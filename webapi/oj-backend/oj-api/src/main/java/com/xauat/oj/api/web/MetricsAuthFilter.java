package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * 监控端点鉴权：所有指标端点（/metrics、/actuator/metrics/**、/actuator/prometheus）
 * 统一要求 {@code Authorization: Bearer <METRICS_TOKEN>}。
 *
 * <p>未配置令牌时默认拒绝（503），仅在显式允许匿名的开发环境放行，避免监控信息意外公开。</p>
 */
public class MetricsAuthFilter extends OncePerRequestFilter {
    private final String token;
    private final boolean allowAnonymous;

    /**
     * @param token          期望的指标访问令牌
     * @param allowAnonymous 未配置令牌时是否允许匿名访问（仅开发环境可开启）
     */
    public MetricsAuthFilter(String token, boolean allowAnonymous) {
        this.token = token == null ? "" : token.trim();
        this.allowAnonymous = allowAnonymous;
    }

    /**
     * 校验 {@code Authorization: Bearer &lt;token&gt;}。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 未配置令牌：默认拒绝，避免指标端点匿名暴露；仅显式允许时放行
        if (token.isBlank()) {
            if (allowAnonymous) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"指标访问令牌未配置\"}");
            return;
        }
        String header = request.getHeader("Authorization");
        String expected = "Bearer " + token;
        // 恒定时间比较，避免通过响应时间逐字节推断令牌
        if (header == null || !MessageDigest.isEqual(header.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
