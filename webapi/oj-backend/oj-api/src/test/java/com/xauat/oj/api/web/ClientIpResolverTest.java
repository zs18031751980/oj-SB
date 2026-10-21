package com.xauat.oj.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客户端 IP 解析测试：默认不信任转发头，仅在显式开启时读取 X-Forwarded-For。
 */
class ClientIpResolverTest {

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        return request;
    }

    @Test
    void ignoresForwardedHeaderWhenUntrusted() {
        assertEquals("10.0.0.9", new ClientIpResolver(false, "").resolve(request()));
    }

    @Test
    void usesFirstForwardedAddressWhenTrusted() {
        assertEquals("203.0.113.7", new ClientIpResolver(true, "").resolve(request()));
    }

    @Test
    void ignoresForwardedHeaderFromNonTrustedPeer() {
        // 直连对端不在可信代理列表中时，即使开启信任也不采纳转发头
        assertEquals("10.0.0.9", new ClientIpResolver(true, "192.0.2.1").resolve(request()));
    }

    @Test
    void acceptsForwardedHeaderFromConfiguredProxy() {
        assertEquals("203.0.113.7", new ClientIpResolver(true, "10.0.0.9,192.0.2.1").resolve(request()));
    }

    @Test
    void fallsBackToRemoteAddrWithoutHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");
        assertEquals("192.0.2.5", new ClientIpResolver(true, "").resolve(request));
    }
}
