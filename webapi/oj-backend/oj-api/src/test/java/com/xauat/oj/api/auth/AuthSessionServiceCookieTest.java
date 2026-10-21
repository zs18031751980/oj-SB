package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 刷新 Cookie 属性测试：HttpOnly、Secure、SameSite、Path 与清理行为。
 */
class AuthSessionServiceCookieTest {

    private AuthSessionService service(boolean secure) {
        return new AuthSessionService(mock(AuthSessionRepository.class), mock(JwtService.class),
                mock(RefreshRecoveryCipher.class), 604800L, secure);
    }

    @Test
    void refreshCookieCarriesSecurityAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service(true).setRefreshCookie(response, "token-value", true);
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("letcoding_refresh=token-value"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Path=/auth"));
        assertTrue(header.contains("Max-Age=604800"));
    }

    @Test
    void clearCookieExpiresWithSameAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service(true).clearRefreshCookie(response);
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Secure"));
    }

    @Test
    void secureFlagFollowsConfiguration() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service(false).setRefreshCookie(response, "token-value", false);
        String header = response.getHeader("Set-Cookie");
        assertFalse(header.contains("Secure"));
    }
}
