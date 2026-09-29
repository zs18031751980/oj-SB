package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** 兼容现有前端的显式 CSRF 保护头；Refresh/Logout 等 Cookie 操作必须携带它。 */
public class CsrfProtectionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
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
