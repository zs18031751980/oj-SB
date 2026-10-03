package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 与旧后端一致的基础安全响应头；敏感路径禁止缓存，HTTPS 请求附加 HSTS。 */
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private static final List<String> NO_STORE_PREFIXES = List.of("/auth/", "/submissions", "/users/me", "/admin/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        String path = request.getRequestURI();
        boolean sensitive = NO_STORE_PREFIXES.stream().anyMatch(path::startsWith) || path.contains("/submission");
        if (sensitive) response.setHeader("Cache-Control", "no-store");
        if (isSecure(request)) response.setHeader("Strict-Transport-Security", "max-age=31536000");
        chain.doFilter(request, response);
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) return true;
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.split(",", 2)[0].trim().equalsIgnoreCase("https");
    }
}
