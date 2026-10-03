package com.xauat.oj.api.user;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.ranking.repository.UserJudgeStatsRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.favorite.repository.FavoriteRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Set;

import java.util.Map;

@RestController
@RequestMapping("/users/me")
public class UserController {
    private final CurrentUser currentUser;
    private final UserJudgeStatsRepository stats;
    private final UserRepository users;
    private final SubmissionRepository submissions;
    private final FavoriteRepository favorites;
    private final Path avatarRoot;

    public UserController(CurrentUser currentUser, UserJudgeStatsRepository stats, UserRepository users,
                          SubmissionRepository submissions, FavoriteRepository favorites, @Value("${oj.upload.avatar-dir:./data/avatars}") String avatarDir) {
        this.currentUser = currentUser; this.stats = stats; this.users = users; this.submissions = submissions; this.favorites = favorites; this.avatarRoot = Path.of(avatarDir).toAbsolutePath().normalize();
    }

    @GetMapping
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return info(currentUser.require(authorization));
    }

    @PatchMapping
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody ProfileRequest request) {
        User user = currentUser.require(authorization);
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim();
            var existing = users.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "该邮箱已被其他用户使用"));
            }
            user.setEmail(email);
        }
        user.updateProfile(request.name(), request.bio(), request.themePreference());
        users.save(user);
        Map<String, Object> body = new java.util.LinkedHashMap<>(info(user));
        body.put("success", true);
        body.put("user_info", info(user));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUser.require(authorization);
        int solved = stats.findById(user.getId()).map(com.xauat.oj.core.ranking.domain.UserJudgeStats::getSolvedCount).orElse(0);
        int submissionCount = submissions.findByUser_IdOrderByIdDesc(user.getId()).size();
        int favoriteCount = favorites.findByUser_IdOrderByIdDesc(user.getId()).size();
        return Map.of("solved", solved, "submissions", submissionCount, "favorites", favoriteCount);
    }

    @PostMapping("/avatar")
    public Map<String, Object> avatar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody AvatarRequest request) {
        User user = currentUser.require(authorization); user.setAvatarUrl(request.avatarUrl()); users.save(user); return info(user);
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAvatar(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestPart("avatar") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "头像不能为空且不能超过 2MB"));
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(); if (!Set.of("image/png", "image/jpeg", "image/webp").contains(contentType)) return ResponseEntity.badRequest().body(Map.of("error", "仅支持 PNG/JPEG/WebP"));
        try { Files.createDirectories(avatarRoot); String extension = contentType.equals("image/png") ? ".png" : contentType.equals("image/webp") ? ".webp" : ".jpg"; String filename = UUID.randomUUID() + extension; Files.write(avatarRoot.resolve(filename), file.getBytes()); var user = currentUser.require(authorization); user.setAvatarUrl("/uploads/avatars/" + filename); users.save(user); return ResponseEntity.ok(Map.of("success", true, "avatar_url", user.getAvatarUrl())); }
        catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("error", "头像保存失败")); }
    }

    private Map<String, Object> info(User user) {
        return Map.of("id", user.getId(), "username", user.getUsername() == null ? "" : user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail(), "name", user.getName() == null ? "" : user.getName(),
                "avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(), "role", user.getRole(),
                "is_active", user.isActive(), "theme_preference", user.getThemePreference());
    }

    public record ProfileRequest(@Size(max = 100) String name, @jakarta.validation.constraints.Email @Size(max = 100) String email,
                                 @Size(max = 500) String bio,
                                 @com.fasterxml.jackson.annotation.JsonProperty("theme_preference")
                                 @com.fasterxml.jackson.annotation.JsonAlias("themePreference") @Size(max = 10) String themePreference) {}
    public record AvatarRequest(@com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
                                @com.fasterxml.jackson.annotation.JsonAlias("avatarUrl")
                                @jakarta.validation.constraints.NotBlank @Size(max = 500) String avatarUrl) {}
}
