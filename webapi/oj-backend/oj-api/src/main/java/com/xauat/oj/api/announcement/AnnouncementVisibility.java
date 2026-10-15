package com.xauat.oj.api.announcement;

import com.xauat.oj.core.user.domain.User;

import java.util.Set;

/**
 * 公告可见性规则（与旧后端 {@code _visible_announcements} 一致）。
 *
 * <p>manager 可见全部；staff 额外可见 {@code staff} 权限的公告；
 * 其余角色仅可见 {@code member}/{@code public}；未发布或未知 permission 一律不可见。</p>
 */
public final class AnnouncementVisibility {
    private AnnouncementVisibility() {
    }

    /** 允许的公告权限取值。 */
    public static final Set<String> VALID_PERMISSIONS = Set.of("member", "public", "staff", "manager");

    /** 从当前用户解析角色，未登录或角色为空时按最小权限 member 处理。 */
    public static String roleOf(User user) {
        return user == null || user.getRole() == null ? "member" : user.getRole();
    }

    /** 判断公告对某角色是否可见；manager 可见全部（含未发布）。 */
    public static boolean visible(String role, boolean published, String permission) {
        if ("manager".equals(role))
            return true;
        if (!published || permission == null)
            return false;
        Set<String> allowed = "staff".equals(role) ? Set.of("member", "public", "staff")
                : Set.of("member", "public");
        return allowed.contains(permission);
    }

    /** 归一化 permission：空值默认 member，非法值返回 null（由调用方转 400）。 */
    public static String normalizePermission(String permission) {
        if (permission == null || permission.isBlank())
            return "member";
        return VALID_PERMISSIONS.contains(permission) ? permission : null;
    }
}
