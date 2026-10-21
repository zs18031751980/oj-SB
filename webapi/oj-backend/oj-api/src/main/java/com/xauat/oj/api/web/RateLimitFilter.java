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
    private final ClientIpResolver clientIp;
    private final int limit;
    private final int publicRunLimit;
    private final boolean publicFailClosed;
    private final Duration window;
    /** @param limit 单窗口允许次数　@param windowSeconds 窗口秒数 */
    public RateLimitFilter(StringRedisTemplate redis, ClientIpResolver clientIp,
            @Value("${oj.rate-limit.per-minute:60}")
            int limit,
            @Value("${oj.rate-limit.code-public-per-minute:20}")
            int publicRunLimit,
            @Value("${oj.rate-limit.public-fail-closed:true}")
            boolean publicFailClosed,
            @Value("${oj.rate-limit.window-seconds:60}")
            long windowSeconds) {
        this.redis = redis;
        this.clientIp = clientIp;
        this.limit = limit;
        this.publicRunLimit = publicRunLimit;
        this.publicFailClosed = publicFailClosed;
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
        // 仅在可信代理后才读取 X-Forwarded-For，否则使用真实远端地址，避免伪造绕过
        String ip = clientIp.resolve(request);
        String path = request.getRequestURI();
        // 匿名执行接口使用独立且更严格的额度
        int effectiveLimit = "/code/run/public".equals(path) ? publicRunLimit : limit;
        String bucket = "oj:rate:" + ip + ":" + path;
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count != null && count == 1L)
                redis.expire(bucket, window);
            if (count != null && count > effectiveLimit) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"请求过于频繁\"}");
                return;
            }
        } catch (RuntimeException exception) {
            // 限流组件故障时的降级：匿名高成本执行默认拒绝，避免失去保护后被刷爆
            if (publicFailClosed && "/code/run/public".equals(path)) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"限流服务不可用，请稍后重试\"}");
                return;
            }
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
