package com.xauat.oj.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析：默认使用 TCP 连接的真实远端地址，只有在明确部署于可信反向代理之后
 * 才读取 {@code X-Forwarded-For}，避免客户端伪造该头绕过基于 IP 的限流。
 */
@Component
public class ClientIpResolver {
    private final boolean trustForwardedFor;

    /**
     * @param trustForwardedFor 是否信任 X-Forwarded-For（仅当代理会覆盖该头时开启）
     */
    public ClientIpResolver(@Value("${oj.proxy.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * 解析客户端 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP；无法获取时返回 {@code unknown}
     */
    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank())
                return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
