package com.xauat.oj.infrastructure.judge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 跨提交编译缓存（对齐旧后端 {@code compile_cache.py}）。
 *
 * <p>以「编译命令 + 源代码」的 SHA-256 作为内容寻址键，把编译产物落盘到缓存目录；
 * 后续相同源码的提交可直接恢复产物、跳过编译。缓存按条目数上限做 LRU 式裁剪，
 * 并在恢复时校验文件存在性，损坏条目自动失效。</p>
 */
@Component
public class CompileCache {
    private final Path root;
    private final boolean enabled;
    private final int maxEntries;

    public CompileCache(
            @Value("${oj.judge.compile-cache.enabled:true}") boolean enabled,
            @Value("${oj.judge.compile-cache.dir:${java.io.tmpdir}/oj-compile-cache}") String directory,
            @Value("${oj.judge.compile-cache.max-entries:500}") int maxEntries) {
        this.enabled = enabled;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** 计算内容寻址键；未启用时返回 null，调用方据此跳过缓存。 */
    public String key(List<String> compileCommand, String code) {
        if (!enabled)
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.join("\u0000", compileCommand).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((code == null ? "" : code).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 将缓存中的编译产物恢复到目标工作目录。
     *
     * @param key    内容寻址键（null 表示未启用）
     * @param target 目标工作目录
     * @return true 表示命中并已恢复，可跳过编译
     */
    public boolean materialize(String key, Path target) {
        if (key == null)
            return false;
        Path entry = root.resolve(key);
        if (!Files.isDirectory(entry))
            return false;
        try {
            copyTree(entry, target);
            // 触摸缓存条目，供裁剪时按最近使用淘汰
            Files.setLastModifiedTime(entry, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            return true;
        } catch (IOException exception) {
            // 条目损坏：删除后按未命中处理
            deleteQuietly(entry);
            return false;
        }
    }

    /**
     * 保存编译产物到缓存。
     *
     * @param key          内容寻址键（null 表示未启用）
     * @param work         编译产物所在工作目录
     * @param excludedName 需要排除的文件名（通常是源文件本身）
     */
    public void store(String key, Path work, String excludedName) {
        if (key == null)
            return;
        Path staging = root.resolve(key + ".tmp-" + java.util.UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            try (Stream<Path> stream = Files.walk(work)) {
                for (Path item : stream.filter(Files::isRegularFile).toList()) {
                    String name = item.getFileName().toString();
                    if (excludedName != null && excludedName.equals(name))
                        continue;
                    Files.copy(item, staging.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Path entry = root.resolve(key);
            deleteQuietly(entry);
            Files.move(staging, entry, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            prune();
        } catch (IOException exception) {
            deleteQuietly(staging);
        }
    }

    /** 按最近修改时间保留最近 {@code maxEntries} 个条目，超出部分删除。 */
    private void prune() {
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> entries = stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(this::lastModified).reversed()).toList();
            for (int i = maxEntries; i < entries.size(); i++)
                deleteQuietly(entries.get(i));
        } catch (IOException ignored) {
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path item : stream.toList()) {
                Path relative = source.relativize(item);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(item))
                    Files.createDirectories(destination);
                else
                    Files.copy(item, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null)
            return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
