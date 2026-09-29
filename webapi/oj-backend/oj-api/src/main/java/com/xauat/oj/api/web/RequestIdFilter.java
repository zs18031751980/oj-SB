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

public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestIdConstants.HEADER);
        if (!StringUtils.hasText(requestId) || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(RequestIdConstants.ATTRIBUTE, requestId);
        response.setHeader(RequestIdConstants.HEADER, requestId);
        filterChain.doFilter(request, response);
    }
}
