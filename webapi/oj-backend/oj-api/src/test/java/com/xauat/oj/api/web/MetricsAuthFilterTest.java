package com.xauat.oj.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控端点鉴权过滤器测试：未配置令牌默认拒绝、错误/正确令牌以及显式匿名放行。
 */
class MetricsAuthFilterTest {

    private MockHttpServletResponse run(MetricsAuthFilter filter, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        if (authorization != null)
            request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void missingTokenDeniedByDefault() throws Exception {
        assertEquals(503, run(new MetricsAuthFilter("", false), null).getStatus());
    }

    @Test
    void anonymousAllowedOnlyWhenConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new MetricsAuthFilter("", true).doFilter(request, response, chain);
        assertNotNull(chain.getRequest(), "允许匿名时应继续过滤器链");
    }

    @Test
    void wrongTokenDenied() throws Exception {
        assertEquals(401, run(new MetricsAuthFilter("secret-token", false), "Bearer wrong").getStatus());
    }

    @Test
    void correctTokenPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new MetricsAuthFilter("secret-token", false).doFilter(request, response, chain);
        assertNotNull(chain.getRequest());
    }
}
