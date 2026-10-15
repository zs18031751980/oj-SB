package com.xauat.oj.api.announcement;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.announcement.domain.Announcement;
import com.xauat.oj.core.announcement.repository.AnnouncementRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公告接口（{@code /announcement}）：公开只读已发布公告，管理端可增删改。
 *
 * <p>未登录用户只能看到已发布公告；manager 可通过 {@code include_unpublished}
 * 查看全部（含草稿）。</p>
 */
@RestController
@RequestMapping("/announcement")
public class AnnouncementController {
    private final AnnouncementRepository announcements;
    private final CurrentUser currentUser;

    public AnnouncementController(AnnouncementRepository announcements, CurrentUser currentUser) {
        this.announcements = announcements;
        this.currentUser = currentUser;
    }

    /**
     * 列出公告。
     *
     * @param authorization       认证头（可空）
     * @param include_unpublished 是否包含未发布公告（仅 manager 生效）
     * @return 按发布时间/创建时间倒序的公告列表
     */
    @GetMapping({"", "/"})
    public ResponseEntity<?> list(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestParam(defaultValue = "false")
            boolean include_unpublished) {
        var user = currentUser.optional(authorization);
        String role = AnnouncementVisibility.roleOf(user);
        List<Announcement> items;
        if (include_unpublished) {
            if (user == null)
                return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
            if (!"manager".equals(role))
                return ResponseEntity.status(403).body(Map.of("error", "权限不足，仅管理员可管理公告"));
            items = announcements.findAll();
        } else {
            // 与旧后端一致：按角色可见的 permission 过滤，未知 permission 一律不公开。
            items = announcements.findByPublishedTrueOrderByPublishedAtDescCreatedAtDesc().stream()
                    .filter(item -> AnnouncementVisibility.visible(role, item.isPublished(), item.getPermission()))
                    .toList();
        }
        var sorted = items.stream()
                .sorted(java.util.Comparator
                        .comparing((Announcement item) -> item.getPublishedAt(),
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(Announcement::getCreatedAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::view).toList();
        return ResponseEntity.ok(sorted);
    }

    /**
     * 查看公告详情。
     *
     * <p>未发布公告对非 manager 伪装成不存在（返回 404），避免泄露草稿。</p>
     *
     * @param authorization 认证头（可空）
     * @param id            公告 id
     * @return 公告视图；不存在或无权查看返回 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        String role = AnnouncementVisibility.roleOf(currentUser.optional(authorization));
        return announcements.findById(id).<ResponseEntity<?>>map(item -> {
            // manager 可见全部；其余角色只能看到「已发布且在可见 permission 内」的公告。
            if (!AnnouncementVisibility.visible(role, item.isPublished(), item.getPermission()))
                return ResponseEntity.status(404).build();
            return ResponseEntity.ok(view(item));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 新建公告（敏感操作）。
     *
     * <p>权限：manager。</p>
     *
     * @param authorization 认证头
     * @param request       公告内容（校验长度）
     * @return 201 与公告视图；非 manager 返回 403
     */
    @PostMapping({"", "/"})
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @jakarta.validation.Valid @RequestBody
            AnnouncementRequest request) {
        var user = currentUser.require(authorization);
        if (!"manager".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        String permission = AnnouncementVisibility.normalizePermission(request.permission());
        if (permission == null)
            return ResponseEntity.badRequest().body(Map.of("error", "permission 无效"));
        Announcement item = announcements.save(Announcement.create(request.title(), request.content(),
                request.category(), permission, String.valueOf(user.getId()), request.published()));
        return ResponseEntity.status(201).body(view(item));
    }

    /**
     * 更新公告（敏感操作）。
     *
     * <p>权限：manager。</p>
     *
     * @param authorization 认证头
     * @param id            公告 id
     * @param request       新的公告内容
     * @return 更新后的公告视图；不存在返回 404，非 manager 返回 403
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id, @jakarta.validation.Valid @RequestBody
            AnnouncementRequest request) {
        var user = currentUser.require(authorization);
        if (!"manager".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        var item = announcements.findById(id).orElse(null);
        if (item == null)
            return ResponseEntity.notFound().build();
        String permission = AnnouncementVisibility.normalizePermission(request.permission());
        if (permission == null)
            return ResponseEntity.badRequest().body(Map.of("error", "permission 无效"));
        item.update(request.title(), request.content(), request.category(), permission, request.published());
        return ResponseEntity.ok(view(announcements.save(item)));
    }

    /**
     * 删除公告（敏感操作）。
     *
     * <p>权限：manager。</p>
     *
     * @param authorization 认证头
     * @param id            公告 id
     * @return 204；不存在返回 404，非 manager 返回 403
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @PathVariable
            Integer id) {
        var user = currentUser.require(authorization);
        if (!"manager".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        if (!announcements.existsById(id))
            return ResponseEntity.notFound().build();
        announcements.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> view(Announcement item) {
        return Map.of("id", item.getId(), "title", item.getTitle() == null ? "" : item.getTitle(), "content",
                item.getContent() == null ? "" : item.getContent(),
                "category", item.getCategory() == null ? "" : item.getCategory(), "permission",
                item.getPermission() == null ? "" : item.getPermission(),
                "is_published", item.isPublished(),
                "published_at", item.getPublishedAt() == null ? "" : item.getPublishedAt().toString(),
                "created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString(),
                "updated_at", item.getUpdatedAt() == null ? "" : item.getUpdatedAt().toString());
    }

    public record AnnouncementRequest(@JsonProperty("is_published") @JsonAlias("published")
    boolean published,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 200)
            String title,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 131072)
            String content,
            @jakarta.validation.constraints.Size(max = 50)
            String category,
            @jakarta.validation.constraints.Size(max = 20)
            String permission) {
    }
}
