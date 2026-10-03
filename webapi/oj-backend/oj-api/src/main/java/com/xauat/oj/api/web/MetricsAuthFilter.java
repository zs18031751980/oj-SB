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

    public MetricsAuthFilter(String token) { this.token = token == null ? "" : token.trim(); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.isBlank()) { chain.doFilter(request, response); return; }
        String header = request.getHeader("Authorization");
        String expected = "Bearer " + token;
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
