package com.xauat.oj.api.web;

import com.xauat.oj.common.constant.RequestIdConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 为每个请求分配/透传请求 id，写入请求属性与响应头，供日志与错误追踪关联。 */
public class RequestIdFilter extends OncePerRequestFilter {
    /**
     * 复用上游传入的请求 id，缺失或过长时生成新的 UUID。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestIdConstants.HEADER);
        // 限制长度可防止攻击者用超长头部污染日志
        if (!StringUtils.hasText(requestId) || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(RequestIdConstants.ATTRIBUTE, requestId);
        response.setHeader(RequestIdConstants.HEADER, requestId);
        filterChain.doFilter(request, response);
    }
}
