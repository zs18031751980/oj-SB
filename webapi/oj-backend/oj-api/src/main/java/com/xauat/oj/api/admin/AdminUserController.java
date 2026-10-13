package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.announcement.repository.AnnouncementRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理端接口（{@code /admin}）：统计概览、用户列表与会话状态管理。
 *
 * <p>所有端点均要求 manager 角色；涉及敏感操作（启停用户、删除用户）由这里集中控制。</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminUserController {
    private final CurrentUser currentUser; private final UserRepository users;
    private final SubmissionRepository submissions; private final AnnouncementRepository announcements;
    public AdminUserController(CurrentUser currentUser, UserRepository users, SubmissionRepository submissions, AnnouncementRepository announcements) {
        this.currentUser = currentUser; this.users = users; this.submissions = submissions; this.announcements = announcements;
    }

    /**
     * 管理端统计概览。
     *
     * <p>权限：manager。返回用户/提交/公告总量，以及最近 5 个用户与公告。</p>
     *
     * @param authorization 认证头
     * @return 统计指标与最近动态
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        requireManager(authorization);
        long active = users.findAll().stream().filter(User::isActive).count();
        // 最近 5 个用户与公告，按 id 倒序近似“最新”。
        var recentUsers = users.findAll().stream().sorted(java.util.Comparator.comparing(User::getId, java.util.Comparator.reverseOrder())).limit(5).map(this::view).toList();
        var recentAnnouncements = announcements.findAll().stream().sorted(java.util.Comparator.comparing(com.xauat.oj.core.announcement.domain.Announcement::getId, java.util.Comparator.reverseOrder())).limit(5).map(a -> Map.<String, Object>of("id", a.getId(), "title", a.getTitle(), "is_published", a.isPublished(), "created_at", a.getCreatedAt() == null ? null : a.getCreatedAt().toString())).toList();
        return Map.of("total_users", users.count(), "active_users", active, "total_submissions", submissions.count(), "total_announcements", announcements.count(), "recent_users", recentUsers, "recent_announcements", recentAnnouncements);
    }

    /**
     * 分页查询用户，支持关键字/角色/状态过滤。
     *
     * <p>权限：manager。{@code admin} 角色别名匹配 {@code manager}；
     * 分页在内存中完成，per_page 上限 200。</p>
     *
     * @param authorization 认证头
     * @param page          页码（从 1 开始）
     * @param per_page      每页条数
     * @param search        用户名或邮箱关键字
     * @param role          角色过滤
     * @param status        状态过滤（active/inactive）
     * @return 分页结果
     */
    @GetMapping("/users")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer per_page,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) String status) {
        requireManager(authorization);
        var matched = users.findAll().stream()
                .filter(u -> search == null || search.isBlank() || (u.getUsername() != null && u.getUsername().toLowerCase().contains(search.toLowerCase())) || (u.getEmail() != null && u.getEmail().toLowerCase().contains(search.toLowerCase())))
                .filter(u -> role == null || role.isBlank() || role.equalsIgnoreCase(u.getRole()) || ("admin".equalsIgnoreCase(role) && "manager".equalsIgnoreCase(u.getRole())))
                .filter(u -> status == null || status.isBlank() || ("active".equalsIgnoreCase(status) && u.isActive()) || ("inactive".equalsIgnoreCase(status) && !u.isActive()))
                .sorted(java.util.Comparator.comparing(User::getId).reversed()).toList();
        int safePage = Math.max(1, page); int size = Math.max(1, Math.min(per_page, 200));
        long total = matched.size(); int from = Math.min((int) total, (safePage - 1) * size); int to = Math.min((int) total, from + size);
        List<Map<String, Object>> data = matched.subList(from, to).stream().map(this::view).toList();
        return Map.of("total", total, "page", safePage, "per_page", size, "data", data);
    }

    /**
     * 启用/停用用户（敏感操作）。
     *
     * <p>权限：manager。仅切换 is_active 标志，不删除数据。</p>
     *
     * @param authorization 认证头
     * @param id            用户 id
     * @param request       目标状态
     * @return 成功标记；用户不存在返回 404
     */
    @PatchMapping("/users/{id}/status")
    @Transactional
    public ResponseEntity<?> status(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @RequestBody StatusRequest request) { requireManager(authorization); var user = users.findById(id).orElse(null); if (user == null) return ResponseEntity.notFound().build(); user.setActive(request.isActive()); return ResponseEntity.ok(Map.of("success", true, "id", id, "is_active", request.isActive())); }

    /**
     * 删除用户（敏感操作）。
     *
     * <p>权限：manager，且为硬删除。</p>
     *
     * @param authorization 认证头
     * @param id            用户 id
     * @return 成功标记；用户不存在返回 404
     */
    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) { requireManager(authorization); if (!users.existsById(id)) return ResponseEntity.notFound().build(); users.deleteById(id); return ResponseEntity.ok(Map.of("success", true, "id", id)); }

    /** 要求当前用户为 manager，否则抛出 FORBIDDEN。 */
    private void requireManager(String authorization) { if (!"manager".equals(currentUser.require(authorization).getRole())) throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "权限不足"); }
    /** 将用户实体转换为对外展示的字段映射。 */
    private Map<String, Object> view(User user) { return Map.of("id", user.getId(), "username", user.getUsername() == null ? "" : user.getUsername(), "email", user.getEmail() == null ? "" : user.getEmail(), "role", user.getRole(), "is_active", user.isActive(), "provider", user.getProvider() == null ? "" : user.getProvider(), "created_at", user.getCreatedAt() == null ? null : user.getCreatedAt().toString(), "last_login", user.getLastLogin() == null ? null : user.getLastLogin().toString()); }
    /** 用户状态变更请求体，兼容 {@code is_active} 字段名。 */
    public record StatusRequest(@JsonProperty("is_active") boolean isActive) {
        @JsonCreator
        public StatusRequest {}
    }
}
