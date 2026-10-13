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

/**
 * 当前用户资料接口（{@code /users/me}）：资料查询/修改、统计与头像。
 *
 * <p>所有端点均需登录；上传头像限制大小与类型并写入受控目录。</p>
 */
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

    /**
     * 获取当前用户资料。
     *
     * @param authorization 认证头
     * @return 用户资料视图
     */
    @GetMapping
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return info(currentUser.require(authorization));
    }

    /**
     * 修改当前用户资料。
     *
     * <p>若提交新邮箱，需保证未被其他用户占用；其余字段通过实体方法统一更新。</p>
     *
     * @param authorization 认证头
     * @param request       昵称/邮箱/简介/主题偏好
     * @return 更新后的资料；邮箱冲突返回 400
     */
    @PatchMapping
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody ProfileRequest request) {
        User user = currentUser.require(authorization);
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim();
            // 邮箱唯一性校验：排除自身后再判断是否被占用。
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

    /**
     * 获取当前用户统计：解题数、提交数、收藏数。
     *
     * @param authorization 认证头
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUser.require(authorization);
        int solved = stats.findById(user.getId()).map(com.xauat.oj.core.ranking.domain.UserJudgeStats::getSolvedCount).orElse(0);
        int submissionCount = submissions.findByUser_IdOrderByIdDesc(user.getId()).size();
        int favoriteCount = favorites.findByUser_IdOrderByIdDesc(user.getId()).size();
        return Map.of("solved", solved, "submissions", submissionCount, "favorites", favoriteCount);
    }

    /**
     * 通过 URL 设置头像。
     *
     * @param authorization 认证头
     * @param request       头像 URL
     * @return 更新后的用户资料
     */
    @PostMapping("/avatar")
    public Map<String, Object> avatar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody AvatarRequest request) {
        User user = currentUser.require(authorization); user.setAvatarUrl(request.avatarUrl()); users.save(user); return info(user);
    }

    /**
     * 上传头像文件（multipart）。
     *
     * <p>限制 2MB 且仅 PNG/JPEG/WebP；以随机文件名落盘并更新用户头像地址。</p>
     *
     * @param authorization 认证头
     * @param file          头像文件
     * @return 成功后的头像地址；校验失败返回 400
     */
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAvatar(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestPart("avatar") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "头像不能为空且不能超过 2MB"));
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(); if (!Set.of("image/png", "image/jpeg", "image/webp").contains(contentType)) return ResponseEntity.badRequest().body(Map.of("error", "仅支持 PNG/JPEG/WebP"));
        try { Files.createDirectories(avatarRoot); String extension = contentType.equals("image/png") ? ".png" : contentType.equals("image/webp") ? ".webp" : ".jpg"; String filename = UUID.randomUUID() + extension; Files.write(avatarRoot.resolve(filename), file.getBytes()); var user = currentUser.require(authorization); user.setAvatarUrl("/uploads/avatars/" + filename); users.save(user); return ResponseEntity.ok(Map.of("success", true, "avatar_url", user.getAvatarUrl())); }
        catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("error", "头像保存失败")); }
    }

    /** 将用户实体转换为对外展示字段映射。 */
    private Map<String, Object> info(User user) {
        return Map.of("id", user.getId(), "username", user.getUsername() == null ? "" : user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail(), "name", user.getName() == null ? "" : user.getName(),
                "avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(), "role", user.getRole(),
                "is_active", user.isActive(), "theme_preference", user.getThemePreference());
    }

    /** 资料修改请求体，兼容 {@code theme_preference} / {@code themePreference} 字段名。 */
    public record ProfileRequest(@Size(max = 100) String name, @jakarta.validation.constraints.Email @Size(max = 100) String email,
                                 @Size(max = 500) String bio,
                                 @com.fasterxml.jackson.annotation.JsonProperty("theme_preference")
                                 @com.fasterxml.jackson.annotation.JsonAlias("themePreference") @Size(max = 10) String themePreference) {}
    /** 头像 URL 请求体，兼容 {@code avatar_url} / {@code avatarUrl} 字段名。 */
    public record AvatarRequest(@com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
                                @com.fasterxml.jackson.annotation.JsonAlias("avatarUrl")
                                @jakarta.validation.constraints.NotBlank @Size(max = 500) String avatarUrl) {}
}
