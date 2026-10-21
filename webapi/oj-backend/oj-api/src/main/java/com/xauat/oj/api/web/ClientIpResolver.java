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
    private final java.util.Set<String> trustedProxies;

    /**
     * @param trustForwardedFor 是否信任 X-Forwarded-For（仅当代理会覆盖该头时开启）
     * @param trustedProxies    可信代理地址列表（逗号分隔）；为空表示信任所有直连对端
     */
    public ClientIpResolver(@Value("${oj.proxy.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${oj.proxy.trusted-proxies:}") String trustedProxies) {
        this.trustForwardedFor = trustForwardedFor;
        this.trustedProxies = java.util.Arrays.stream(trustedProxies == null ? new String[0] : trustedProxies.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 解析客户端 IP。
     *
     * <p>只有同时满足「开启信任」且「直连对端在可信代理列表中（列表为空时视为信任全部）」
     * 才采纳 {@code X-Forwarded-For}，否则一律使用真实远端地址。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP；无法获取时返回 {@code unknown}
     */
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!trustForwardedFor)
            return remote;
        // 配置了可信代理列表时，仅接受来自这些对端的转发头
        if (!trustedProxies.isEmpty() && !trustedProxies.contains(remote))
            return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
            return forwarded.split(",", 2)[0].trim();
        return remote;
    }
}
