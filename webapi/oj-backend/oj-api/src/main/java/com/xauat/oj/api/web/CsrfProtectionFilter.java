package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * 兼容现有前端的显式 CSRF 保护头；Refresh/Logout 等 Cookie 操作必须携带它。
 *
 * <p>因为刷新令牌以 Cookie 形式随请求自动发送，浏览器跨站请求会天然携带该凭据，
 * 故要求非 GET 的 {@code /auth/*} 请求显式提供 {@code X-CSRF-Protection: 1}，
 * 使攻击者无法在不知情页面中构造带 Cookie 的写操作。</p>
 */
public class CsrfProtectionFilter extends OncePerRequestFilter {
    /**
     * 对 {@code /auth/*} 的非安全方法强制校验 CSRF 头。
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
        // 仅拦截会改变状态的写操作；GET 等安全方法无需该头
        if (request.getRequestURI().startsWith("/auth/") && !"GET".equalsIgnoreCase(request.getMethod()) &&
                !"1".equals(request.getHeader("X-CSRF-Protection"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"CSRF protection header required\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
