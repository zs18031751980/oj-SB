package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 供应商角色信任策略测试：未信任供应商一律 member；可信供应商默认最多 staff。
 */
class ProviderUserSyncRoleTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ProviderUserSync sync(String trusted, boolean allowManager) {
        return new ProviderUserSync(mock(UserRepository.class), mock(AuthSessionRepository.class), trusted,
                allowManager);
    }

    @Test
    void untrustedProviderCannotElevate() throws Exception {
        var profile = mapper.readTree("{\"roles\":[\"admin\"]}");
        assertEquals("member", sync("", false).effectiveProviderRole("iOSClub", profile));
        assertEquals("member", sync("github", false).effectiveProviderRole("iOSClub", profile));
    }

    @Test
    void trustedProviderCapsManagerToStaffByDefault() throws Exception {
        var profile = mapper.readTree("{\"roles\":[\"administrator\"]}");
        assertEquals("staff", sync("iOSClub", false).effectiveProviderRole("iOSClub", profile));
    }

    @Test
    void trustedProviderCanGrantManagerOnlyWhenExplicitlyAllowed() throws Exception {
        var profile = mapper.readTree("{\"roles\":[\"admin\"]}");
        assertEquals("manager", sync("iOSClub", true).effectiveProviderRole("iOSClub", profile));
    }

    @Test
    void trustedProviderKeepsStaff() throws Exception {
        var profile = mapper.readTree("{\"roles\":[\"staff\"]}");
        assertEquals("staff", sync("iOSClub", false).effectiveProviderRole("iOSClub", profile));
    }
}
