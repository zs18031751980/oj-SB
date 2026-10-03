package com.xauat.oj.api.learning;

import com.xauat.oj.api.auth.CurrentUser;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 兼容原 Flask 学习资料接口：树/文件/资源/重扫，并拒绝路径穿越。 */
@RestController
@RequestMapping("/learn-resources")
public class LearnResourceController {
    private final LearnScannerService scanner;
    private final CurrentUser currentUser;

    public LearnResourceController(LearnScannerService scanner, CurrentUser currentUser) {
        this.scanner = scanner; this.currentUser = currentUser;
    }

    @GetMapping("/tree")
    public ResponseEntity<?> tree() {
        if (!scanner.available()) return ResponseEntity.status(500).body(Map.of("error", "学习资料暂时不可用"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", scanner.tree());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/file/**")
    public ResponseEntity<?> file(HttpServletRequest request) {
        String path = pathAfter(request, "/file/");
        return scanner.readMarkdown(path).<ResponseEntity<?>>map(content -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", content);
            return ResponseEntity.ok(result);
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "文件不存在")));
    }

    @GetMapping("/asset/**")
    public ResponseEntity<?> asset(HttpServletRequest request) {
        String path = pathAfter(request, "/asset/");
        return scanner.resolveAsset(path).<ResponseEntity<?>>map(resolved -> {
            try {
                String contentType = Files.probeContentType(resolved);
                return ResponseEntity.ok().contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                        .header("Cache-Control", "public, max-age=86400").body(new FileSystemResource(resolved));
            } catch (IOException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", "非法路径"));
            }
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "资源不存在")));
    }

    /** 管理员重新扫描目录树。 */
    @PostMapping("/rescan")
    public ResponseEntity<?> rescan(@RequestHeader(value = "Authorization", required = false) String authorization) {
        var user = currentUser.require(authorization);
        String role = user.getRole() == null ? "" : user.getRole().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("admin", "staff", "manager").contains(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        scanner.rescan();
        return ResponseEntity.ok(Map.of("success", true, "message", "目录已重新扫描"));
    }

    private String pathAfter(HttpServletRequest request, String marker) {
        String uri = request.getRequestURI();
        int index = uri.indexOf(marker);
        if (index < 0) return "";
        try { return java.net.URLDecoder.decode(uri.substring(index + marker.length()), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception exception) { return uri.substring(index + marker.length()); }
    }
}
