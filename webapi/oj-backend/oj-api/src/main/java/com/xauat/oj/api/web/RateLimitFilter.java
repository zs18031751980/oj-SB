package com.xauat.oj.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

/** 对高成本写操作做短窗口限流；Redis 故障时放行，避免限流组件反过来阻断核心 API。 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;
    /** @param limit 单窗口允许次数　@param windowSeconds 窗口秒数 */
    public RateLimitFilter(StringRedisTemplate redis, @Value("${oj.rate-limit.per-minute:60}")
    int limit,
            @Value("${oj.rate-limit.window-seconds:60}")
            long windowSeconds) {
        this.redis = redis;
        this.limit = limit;
        this.window = Duration.ofSeconds(windowSeconds);
    }
    /**
     * 对受限路由做按 IP + 路径的固定窗口计数限流。
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
        if (!isLimitedRoute(request)) {
            chain.doFilter(request, response);
            return;
        }
        // 反向代理下取 X-Forwarded-For 第一段作为真实客户端 IP，否则回退 RemoteAddr
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank())
            ip = request.getRemoteAddr();
        else
            ip = ip.split(",", 2)[0].trim();
        String bucket = "oj:rate:" + ip + ":" + request.getRequestURI();
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count != null && count == 1L)
                redis.expire(bucket, window);
            if (count != null && count > limit) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"请求过于频繁\"}");
                return;
            }
        } catch (RuntimeException ignored) {
        }
        chain.doFilter(request, response);
    }
    /** 判定是否为需要限流的高成本写路由。 */
    private boolean isLimitedRoute(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()))
            return false;
        String path = request.getRequestURI();
        return path.equals("/submissions") || path.matches("/contests/[^/]+/problems/[^/]+/submit")
                || path.equals("/code/run") || path.equals("/code/run/public") || path.startsWith("/auth/login");
    }
}
