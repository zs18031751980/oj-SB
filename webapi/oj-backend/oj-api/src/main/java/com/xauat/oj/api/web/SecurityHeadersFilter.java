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

    /**
     * 为所有响应附加基础安全头，并按路径决定是否禁止缓存。
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
        // 禁止 MIME 嗅探、限制 Referer 泄露、禁止被内嵌到 iframe（防点击劫持）
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        String path = request.getRequestURI();
        // 认证、提交、个人与后台等敏感响应禁止中间层/浏览器缓存
        boolean sensitive = NO_STORE_PREFIXES.stream().anyMatch(path::startsWith) || path.contains("/submission");
        if (sensitive)
            response.setHeader("Cache-Control", "no-store");
        // 仅在 HTTPS（含代理转发声明）时下发 HSTS
        if (isSecure(request))
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        chain.doFilter(request, response);
    }

    /** 判断请求是否经由 HTTPS，兼容反向代理设置的 X-Forwarded-Proto。 */
    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure())
            return true;
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.split(",", 2)[0].trim().equalsIgnoreCase("https");
    }
}
