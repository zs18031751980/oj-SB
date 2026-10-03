package com.xauat.oj.api.learning;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.learning.domain.LearnBrowsingHistory;
import com.xauat.oj.core.learning.domain.LearnFavorite;
import com.xauat.oj.core.learning.repository.LearnBrowsingHistoryRepository;
import com.xauat.oj.core.learning.repository.LearnFavoriteRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class LearningController {
    private final CurrentUser currentUser;
    private final LearnFavoriteRepository favorites;
    private final LearnBrowsingHistoryRepository history;

    public LearningController(CurrentUser currentUser, LearnFavoriteRepository favorites, LearnBrowsingHistoryRepository history) {
        this.currentUser = currentUser; this.favorites = favorites; this.history = history;
    }

    @GetMapping("/learn-favorites")
    public Map<String, Object> favorites(@RequestHeader(value = "Authorization", required = false) String authorization) {
        List<Map<String, Object>> data = favorites.findByUser_IdOrderByIdDesc(currentUser.require(authorization).getId()).stream()
                .map(item -> Map.<String, Object>of("resource_id", item.getResourceId(), "favorited_at", item.getCreatedAt() == null ? null : item.getCreatedAt().toString())).toList();
        return Map.of("data", data, "total", data.size());
    }

    @PostMapping("/learn-favorites/{*resourcePath}")
    public Map<String, Object> addFavorite(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String resourcePath) {
        var user = currentUser.require(authorization);
        String resourceId = resourceId(resourcePath, false);
        if (!favorites.existsByUser_IdAndResourceId(user.getId(), resourceId)) favorites.save(LearnFavorite.of(user, resourceId));
        return Map.of("resource_id", resourceId, "favorited", true);
    }

    @DeleteMapping("/learn-favorites/{*resourcePath}")
    @Transactional
    public Map<String, Object> removeFavorite(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String resourcePath) {
        String resourceId = resourceId(resourcePath, true);
        favorites.deleteByUser_IdAndResourceId(currentUser.require(authorization).getId(), resourceId);
        return Map.of("resource_id", resourceId, "favorited", false);
    }

    @GetMapping("/learn-favorites/{*resourcePath}")
    public Map<String, Object> favoriteStatus(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String resourcePath) {
        String resourceId = resourceId(resourcePath, true);
        return Map.of("resource_id", resourceId, "favorited", favorites.existsByUser_IdAndResourceId(currentUser.require(authorization).getId(), resourceId));
    }

    /** 学习资源 id 含 `/`，路径变量捕获其余部分；去掉前导 `/` 与结尾 `/status`。 */
    private String resourceId(String resourcePath, boolean stripStatus) {
        String path = resourcePath == null ? "" : resourcePath;
        if (path.startsWith("/")) path = path.substring(1);
        if (stripStatus && path.endsWith("/status")) path = path.substring(0, path.length() - "/status".length());
        return path;
    }

    @GetMapping("/learn-history")
    public Map<String, Object> history(@RequestHeader(value = "Authorization", required = false) String authorization) {
        List<Map<String, Object>> data = history.findByUser_IdOrderByBrowsedAtDesc(currentUser.require(authorization).getId()).stream()
                .map(item -> Map.<String, Object>of("resource_id", item.getResourceId(), "browsed_at", item.getBrowsedAt() == null ? null : item.getBrowsedAt().toString())).toList();
        return Map.of("data", data, "total", data.size());
    }

    @PostMapping("/learn-history")
    public Map<String, Object> record(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody HistoryRequest request) {
        var item = history.save(LearnBrowsingHistory.of(currentUser.require(authorization), request.resourceId()));
        return Map.of("id", item.getId(), "resource_id", item.getResourceId(), "success", true);
    }

    @DeleteMapping("/learn-history")
    @Transactional
    public Map<String, Object> clear(@RequestHeader(value = "Authorization", required = false) String authorization) {
        history.deleteByUser_Id(currentUser.require(authorization).getId());
        return Map.of("cleared", true, "success", true);
    }

    public record HistoryRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("resource_id")
            @com.fasterxml.jackson.annotation.JsonAlias("resourceId") @NotBlank String resourceId) {}
}
