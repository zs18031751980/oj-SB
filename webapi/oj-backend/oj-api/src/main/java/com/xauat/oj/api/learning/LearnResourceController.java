package com.xauat.oj.api.learning;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 兼容原 Flask 学习资料接口，并拒绝路径穿越。 */
@RestController
@RequestMapping("/learn-resources")
public class LearnResourceController {
    private final Path root;
    public LearnResourceController() { String configured = System.getenv("OJ_LEARN_ROOT"); root = Paths.get(configured == null || configured.isBlank() ? "./learn" : configured).toAbsolutePath().normalize(); }

    @GetMapping("/tree")
    public ResponseEntity<?> tree() throws IOException { if (!Files.isDirectory(root)) return ResponseEntity.ok(Map.of("root", root.toString(), "children", List.of())); return ResponseEntity.ok(Map.of("root", root.getFileName() == null ? "learn" : root.getFileName().toString(), "children", node(root))); }

    @GetMapping("/file/**")
    public ResponseEntity<?> file(HttpServletRequest request) throws IOException { Path path = safePath(request, "/file/"); if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build(); return ResponseEntity.ok().contentType(MediaType.TEXT_MARKDOWN).body(Files.readString(path)); }

    @GetMapping("/asset/**")
    public ResponseEntity<?> asset(HttpServletRequest request) { try { Path path = safePath(request, "/asset/"); if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build(); return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(path)); } catch (IOException e) { return ResponseEntity.badRequest().body(Map.of("error", "资源路径无效")); } }

    private List<Map<String,Object>> node(Path directory) throws IOException { List<Map<String,Object>> result = new ArrayList<>(); try (var stream = Files.list(directory)) { for (Path child : stream.sorted().toList()) { Map<String,Object> item = new HashMap<>(); item.put("name", child.getFileName().toString()); item.put("type", Files.isDirectory(child) ? "directory" : "file"); item.put("path", root.relativize(child).toString().replace(FileSystems.getDefault().getSeparator(), "/")); if (Files.isDirectory(child)) item.put("children", node(child)); result.add(item); } } return result; }
    private Path safePath(jakarta.servlet.http.HttpServletRequest request, String marker) throws IOException { String uri = request.getRequestURI(); int index = uri.indexOf(marker); if (index < 0) throw new IOException("invalid path"); String relative = uri.substring(index + marker.length()); Path path = root.resolve(relative).normalize(); if (!path.startsWith(root)) throw new IOException("path traversal"); return path; }
}
