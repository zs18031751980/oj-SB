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

@RestController
@RequestMapping("/announcement")
public class AnnouncementController {
    private final AnnouncementRepository announcements;
    private final CurrentUser currentUser;

    public AnnouncementController(AnnouncementRepository announcements, CurrentUser currentUser) { this.announcements = announcements; this.currentUser = currentUser; }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestParam(defaultValue = "false") boolean include_unpublished) {
        boolean admin = isManager(authorization);
        List<Announcement> items = (include_unpublished && admin)
                ? announcements.findAll()
                : announcements.findByPublishedTrueOrderByPublishedAtDescCreatedAtDesc();
        return items.stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        return announcements.findById(id).<ResponseEntity<?>>map(item -> {
            if (!item.isPublished() && !isManager(authorization)) return ResponseEntity.status(404).build();
            return ResponseEntity.ok(view(item));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping({"", "/"})
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization, @jakarta.validation.Valid @RequestBody AnnouncementRequest request) {
        var user = currentUser.require(authorization); if (!"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        Announcement item = announcements.save(Announcement.create(request.title(), request.content(), request.category(), request.permission(), String.valueOf(user.getId()), request.published()));
        return ResponseEntity.status(201).body(view(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id, @jakarta.validation.Valid @RequestBody AnnouncementRequest request) {
        var user = currentUser.require(authorization); if (!"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        var item = announcements.findById(id).orElse(null); if (item == null) return ResponseEntity.notFound().build();
        item.update(request.title(), request.content(), request.category(), request.permission(), request.published());
        return ResponseEntity.ok(view(announcements.save(item)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Integer id) {
        var user = currentUser.require(authorization); if (!"manager".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        if (!announcements.existsById(id)) return ResponseEntity.notFound().build(); announcements.deleteById(id); return ResponseEntity.noContent().build();
    }

    private boolean isManager(String authorization) { try { return "manager".equals(currentUser.require(authorization).getRole()); } catch (RuntimeException e) { return false; } }

    private Map<String, Object> view(Announcement item) {
        return Map.of("id", item.getId(), "title", item.getTitle() == null ? "" : item.getTitle(), "content", item.getContent() == null ? "" : item.getContent(),
                "category", item.getCategory() == null ? "" : item.getCategory(), "permission", item.getPermission() == null ? "" : item.getPermission(),
                "is_published", item.isPublished(),
                "published_at", item.getPublishedAt() == null ? "" : item.getPublishedAt().toString(),
                "created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString(),
                "updated_at", item.getUpdatedAt() == null ? "" : item.getUpdatedAt().toString());
    }

    public record AnnouncementRequest(@JsonProperty("is_published") @JsonAlias("published") boolean published,
                                      @jakarta.validation.constraints.Size(max = 200) String title,
                                      @jakarta.validation.constraints.Size(max = 131072) String content,
                                      @jakarta.validation.constraints.Size(max = 50) String category,
                                      @jakarta.validation.constraints.Size(max = 20) String permission) {}
}
