package com.xauat.oj.api.learning;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学习资料目录扫描与资源解析（移植自旧后端 learn_scanner_service）。
 * 结果带短 TTL 缓存，/rescan 可强制刷新；所有路径做根目录越界校验。
 */
@Service
public class LearnScannerService {
    private static final int MAX_DEPTH = 20;
    private static final long CACHE_TTL_MS = 60_000;
    private static final Set<String> SKIP_DIRS = Set.of(
            "__pycache__", "node_modules", ".git", "images", "assets", ".obsidian",
            "ai-skills教学", "runoob", "w3", "opencode-skills");
    private static final Pattern PREFIX = Pattern.compile("^\\d+[-_.\\s]+");
    private static final Pattern TITLE = Pattern.compile("^#\\s+(.+)", Pattern.MULTILINE);

    private final Path root;
    private volatile Map<String, Object> cache;
    private volatile long cacheTs;

    public LearnScannerService() {
        String configured = System.getenv("OJ_LEARN_ROOT");
        this.root = Paths.get(configured == null || configured.isBlank() ? "./learn" : configured).toAbsolutePath()
                .normalize();
    }

    /** 资料根目录（绝对规范化路径）。 */
    public Path root() {
        return root;
    }
    /** 根目录是否为可读目录。 */
    public boolean available() {
        return Files.isDirectory(root);
    }

    /**
     * 返回目录树，命中 TTL 缓存则直接复用。
     *
     * @return 根节点（无内容时为 {@code null}）
     */
    public synchronized Map<String, Object> tree() {
        if (cache != null && System.currentTimeMillis() - cacheTs < CACHE_TTL_MS)
            return cache;
        cache = scan(root, "", 0);
        cacheTs = System.currentTimeMillis();
        return cache;
    }

    /** 清空缓存并立即重建目录树。 */
    public synchronized void rescan() {
        cache = null;
        tree();
    }

    /**
     * 递归扫描目录，构建目录/文件节点。
     *
     * @param directory 当前目录
     * @param relative  相对根目录的路径
     * @param depth     当前深度（超过 {@link #MAX_DEPTH} 终止）
     * @return 节点；无有效内容返回 {@code null}
     */
    private Map<String, Object> scan(Path directory, String relative, int depth) {
        if (depth > MAX_DEPTH || !Files.isDirectory(directory))
            return null;
        List<Map<String, Object>> children = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path entry : stream.sorted().toList()) {
                String name = entry.getFileName().toString();
                // 跳过隐藏文件/目录。
                if (name.startsWith("."))
                    continue;
                String entryRel = relative.isEmpty() ? name : relative + "/" + name;
                if (Files.isDirectory(entry)) {
                    // 跳过明确不需要展示的目录。
                    if (SKIP_DIRS.contains(name.toLowerCase(Locale.ROOT)))
                        continue;
                    Map<String, Object> child = scan(entry, entryRel, depth + 1);
                    if (child != null && !((List<?>) child.get("children")).isEmpty())
                        children.add(child);
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    Map<String, Object> file = new LinkedHashMap<>();
                    file.put("id", makeId(entryRel));
                    file.put("name", sanitize(name.substring(0, name.length() - 3)));
                    file.put("type", "file");
                    file.put("path", entryRel);
                    file.put("size", Files.size(entry));
                    file.put("mtime", Files.getLastModifiedTime(entry).toMillis() / 1000);
                    children.add(file);
                }
            }
        } catch (IOException exception) {
            return null;
        }
        if (children.isEmpty() && relative.isEmpty())
            return null;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", makeId(relative.isEmpty() ? "__root__" : relative));
        node.put("name", relative.isEmpty() ? "学习资料" : sanitize(directory.getFileName().toString()));
        node.put("type", "folder");
        node.put("path", relative);
        node.put("children", children);
        return node;
    }

    /**
     * 读取 Markdown 文件内容与标题。
     *
     * <p>标题优先取正文首个一级标题，否则用文件名。仅允许 .md 且路径不得越界。</p>
     *
     * @param relativePath 相对根目录路径
     * @return 内容/标题/路径/修改时间；不可用时返回空
     */
    public Optional<Map<String, Object>> readMarkdown(String relativePath) {
        Path resolved = safeResolve(relativePath);
        if (resolved == null || !Files.isRegularFile(resolved)
                || !resolved.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
            return Optional.empty();
        try {
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            Matcher matcher = TITLE.matcher(content);
            String title = matcher.find()
                    ? matcher.group(1).trim()
                    : resolved.getFileName().toString().replace(".md", "");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("title", title);
            result.put("path", relativePath);
            result.put("mtime", Files.getLastModifiedTime(resolved).toMillis() / 1000);
            return Optional.of(result);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * 解析静态资源路径。
     *
     * @param relativePath 相对根目录路径
     * @return 实际文件路径；不存在或越界返回空
     */
    public Optional<Path> resolveAsset(String relativePath) {
        Path resolved = safeResolve(relativePath);
        return resolved != null && Files.isRegularFile(resolved) ? Optional.of(resolved) : Optional.empty();
    }

    /** 安全解析路径：规范化后必须仍在根目录内，防止路径穿越。 */
    private Path safeResolve(String relativePath) {
        if (relativePath == null)
            return null;
        try {
            Path resolved = root.resolve(relativePath).normalize();
            return resolved.startsWith(root) ? resolved : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 去掉文件名前缀中的序号/分隔符（如 "01-xxx" -> "xxx"）。 */
    private static String sanitize(String name) {
        String cleaned = PREFIX.matcher(name).replaceFirst("").trim();
        return cleaned.isEmpty() ? name : cleaned;
    }

    /** 依据相对路径生成稳定短 id（MD5 前 12 位，异常时退回 hashCode）。 */
    private static String makeId(String relativePath) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(relativePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception exception) {
            return Integer.toHexString(relativePath.hashCode());
        }
    }
}
