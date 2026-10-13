package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 浏览器写请求的 Origin 边界：非 GET/HEAD/OPTIONS 且携带 Origin 时，
 * 仅允许白名单来源或同源。Bearer API 的非浏览器调用通常不带 Origin，予以放行。
 */
public class OriginValidationFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    /**
     * @param origins 逗号分隔的允许来源白名单
     */
    public OriginValidationFilter(String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    /**
     * 对非安全方法的写请求校验 Origin。
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
        String method = request.getMethod();
        // 只读方法不改变状态，跳过 Origin 校验
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        // 无 Origin 通常是非浏览器 API 调用（Bearer 鉴权），无 CSRF 风险故放行
        if (origin != null && !origin.isBlank() && !allowedOrigins.contains(origin) && !sameHost(origin, request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"不允许的请求来源\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 判断 Origin 的主机是否与请求主机同源（兼容反向代理的 X-Forwarded-Host）。 */
    private boolean sameHost(String origin, HttpServletRequest request) {
        try {
            String originHost = URI.create(origin).getHost();
            String forwarded = request.getHeader("X-Forwarded-Host");
            // 代理链中取第一个 Host；再剥掉端口只比主机名
            String host = forwarded != null && !forwarded.isBlank() ? forwarded.split(",", 2)[0].trim() : request.getHeader("Host");
            if (host == null) return false;
            String requestHost = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
            return originHost != null && originHost.equalsIgnoreCase(requestHost);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
