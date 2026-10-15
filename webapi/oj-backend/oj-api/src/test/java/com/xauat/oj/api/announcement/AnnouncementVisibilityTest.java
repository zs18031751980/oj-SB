package com.xauat.oj.api.announcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 公告可见性规则单元测试：覆盖角色分层、未发布与未知权限等边界。
 */
class AnnouncementVisibilityTest {

    @Test
    void managerSeesEverything() {
        assertTrue(AnnouncementVisibility.visible("manager", true, "manager"));
        // 未发布公告 manager 亦可见
        assertTrue(AnnouncementVisibility.visible("manager", false, "manager"));
    }

    @Test
    void memberSeesOnlyMemberAndPublic() {
        assertTrue(AnnouncementVisibility.visible("member", true, "member"));
        assertTrue(AnnouncementVisibility.visible("member", true, "public"));
        assertFalse(AnnouncementVisibility.visible("member", true, "staff"));
        assertFalse(AnnouncementVisibility.visible("member", true, "manager"));
    }

    @Test
    void staffAdditionallySeesStaff() {
        assertTrue(AnnouncementVisibility.visible("staff", true, "staff"));
        assertFalse(AnnouncementVisibility.visible("staff", true, "manager"));
    }

    @Test
    void unpublishedAndUnknownPermissionAreHidden() {
        assertFalse(AnnouncementVisibility.visible("member", false, "public"));
        assertFalse(AnnouncementVisibility.visible("staff", true, "unknown"));
        assertFalse(AnnouncementVisibility.visible("member", true, null));
    }

    @Test
    void normalizePermissionDefaultsAndRejectsInvalid() {
        assertEquals("member", AnnouncementVisibility.normalizePermission(null));
        assertEquals("member", AnnouncementVisibility.normalizePermission("  "));
        assertEquals("staff", AnnouncementVisibility.normalizePermission("staff"));
        assertNull(AnnouncementVisibility.normalizePermission("nope"));
    }

    @Test
    void roleOfFallsBackToMemberForAnonymous() {
        assertEquals("member", AnnouncementVisibility.roleOf(null));
    }
}
