package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/** 与旧后端一致：/metrics（Prometheus）需要 METRICS_TOKEN；未配置时开发环境放行。 */
public class MetricsAuthFilter extends OncePerRequestFilter {
    private final String token;

    /** @param token 期望的指标访问令牌，空字符串表示不启用认证（开发环境）。 */
    public MetricsAuthFilter(String token) {
        this.token = token == null ? "" : token.trim();
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
        // 未配置令牌视为开发环境，直接放行
        if (token.isBlank()) {
            chain.doFilter(request, response);
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
