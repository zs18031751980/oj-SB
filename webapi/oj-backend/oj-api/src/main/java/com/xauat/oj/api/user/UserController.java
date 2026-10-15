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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

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
            SubmissionRepository submissions, FavoriteRepository favorites,
            @Value("${oj.upload.avatar-dir:./data/avatars}")
            String avatarDir) {
        this.currentUser = currentUser;
        this.stats = stats;
        this.users = users;
        this.submissions = submissions;
        this.favorites = favorites;
        this.avatarRoot = Path.of(avatarDir).toAbsolutePath().normalize();
    }

    /**
     * 获取当前用户资料。
     *
     * @param authorization 认证头
     * @return 用户资料视图
     */
    @GetMapping
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false)
    String authorization) {
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
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @RequestBody
            ProfileRequest request) {
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
    public Map<String, Object> stats(@RequestHeader(value = "Authorization", required = false)
    String authorization) {
        User user = currentUser.require(authorization);
        int solved = stats.findById(user.getId()).map(com.xauat.oj.core.ranking.domain.UserJudgeStats::getSolvedCount)
                .orElse(0);
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
    public Map<String, Object> avatar(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @Valid @RequestBody
            AvatarRequest request) {
        User user = currentUser.require(authorization);
        user.setAvatarUrl(request.avatarUrl());
        users.save(user);
        return info(user);
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
    public ResponseEntity<?> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestPart("avatar")
            MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            return ResponseEntity.badRequest().body(Map.of("error", "头像不能为空且不能超过 2MB"));
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(contentType))
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 PNG/JPEG/WebP/GIF"));
        try {
            // 与旧后端一致：解码后统一重编码为 JPEG，顺带做像素炸弹与超大尺寸防护
            byte[] normalized = normalizeAvatar(file.getBytes());
            Files.createDirectories(avatarRoot);
            String filename = UUID.randomUUID() + ".jpg";
            Files.write(avatarRoot.resolve(filename), normalized);
            var user = currentUser.require(authorization);
            user.setAvatarUrl("/uploads/avatars/" + filename);
            users.save(user);
            return ResponseEntity.ok(Map.of("success", true, "avatar_url", user.getAvatarUrl()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", "头像保存失败"));
        }
    }

    /** 头像像素上限（与旧后端 Pillow 防护一致：1600 万像素）。 */
    private static final long MAX_AVATAR_PIXELS = 16_000_000L;
    /** 归一化后的最长边上限。 */
    private static final int MAX_AVATAR_DIMENSION = 1024;

    /**
     * 解码图片并统一重编码为 JPEG。
     *
     * <p>先按图片头尺寸判断像素量，避免解码超大图造成内存耗尽；随后等比缩小到 1024 以内，
     * 并在白底上合成以去除 alpha 通道后以质量 0.85 输出 JPEG。</p>
     *
     * @param data 原始图片字节
     * @return JPEG 字节
     * @throws IOException              读取/编码失败
     * @throws IllegalArgumentException 无法识别或尺寸超限
     */
    private byte[] normalizeAvatar(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null)
                throw new IllegalArgumentException("无法识别的图片");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new IllegalArgumentException("无法识别的图片");
            ImageWriter writer = null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_AVATAR_PIXELS)
                    throw new IllegalArgumentException("图片尺寸超出限制");
                BufferedImage decoded = reader.read(0);
                BufferedImage scaled = scaleDown(toRgb(decoded), MAX_AVATAR_DIMENSION);
                var iterator = ImageIO.getImageWritersByFormatName("jpeg");
                if (!iterator.hasNext())
                    throw new IOException("缺少 JPEG 编码器");
                writer = iterator.next();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
                    writer.setOutput(imageOutput);
                    var params = writer.getDefaultWriteParam();
                    params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.85f);
                    writer.write(null, new IIOImage(scaled, null, null), params);
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
                if (writer != null)
                    writer.dispose();
            }
        }
    }

    /** 将任意图片绘制到 RGB 画布（白底），去除 alpha 以便编码为 JPEG。 */
    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    /** 等比缩小图片，使其最长边不超过 {@code maxDimension}；已足够小则原样返回。 */
    private BufferedImage scaleDown(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension)
            return source;
        double ratio = (double) maxDimension / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
    }

    /** 将用户实体转换为对外展示字段映射。 */
    private Map<String, Object> info(User user) {
        return Map.of("id", user.getId(), "username", user.getUsername() == null ? "" : user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail(), "name",
                user.getName() == null ? "" : user.getName(),
                "avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(), "role", user.getRole(),
                "is_active", user.isActive(), "theme_preference", user.getThemePreference());
    }

    /** 资料修改请求体，兼容 {@code theme_preference} / {@code themePreference} 字段名。 */
    public record ProfileRequest(@Size(max = 100)
    String name,
            @jakarta.validation.constraints.Email @Size(max = 100)
            String email,
            @Size(max = 500)
            String bio,
            @com.fasterxml.jackson.annotation.JsonProperty("theme_preference")
            @com.fasterxml.jackson.annotation.JsonAlias("themePreference")
            @Size(max = 10)
            String themePreference) {
    }
    /** 头像 URL 请求体，兼容 {@code avatar_url} / {@code avatarUrl} 字段名。 */
    public record AvatarRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
            @com.fasterxml.jackson.annotation.JsonAlias("avatarUrl")
            @jakarta.validation.constraints.NotBlank
            @Size(max = 500)
            String avatarUrl) {
    }
}
