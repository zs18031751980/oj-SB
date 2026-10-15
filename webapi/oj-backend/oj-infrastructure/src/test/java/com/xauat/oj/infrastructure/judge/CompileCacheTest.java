package com.xauat.oj.infrastructure.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编译缓存单元测试：验证内容寻址、命中恢复、源文件排除与按上限裁剪。
 */
class CompileCacheTest {

    @Test
    void storeThenMaterializeRestoresArtifacts(@TempDir Path root, @TempDir Path work) throws IOException {
        CompileCache cache = new CompileCache(true, root.toString(), 10);
        Files.writeString(work.resolve("main.cpp"), "int main(){}");
        Files.writeString(work.resolve("main"), "binary");
        String key = cache.key(List.of("g++", "-o", "main", "main.cpp"), "int main(){}");
        assertNotNull(key);
        cache.store(key, work, "main.cpp");

        Path target = Files.createTempDirectory(root, "restore-");
        assertTrue(cache.materialize(key, target));
        assertTrue(Files.exists(target.resolve("main")));
        // 源文件被排除，不进入缓存
        assertFalse(Files.exists(target.resolve("main.cpp")));
    }

    @Test
    void missWhenKeyUnknown(@TempDir Path root) throws IOException {
        CompileCache cache = new CompileCache(true, root.toString(), 10);
        assertFalse(cache.materialize("deadbeef", Files.createTempDirectory(root, "w-")));
    }

    @Test
    void disabledCacheIsInert(@TempDir Path root, @TempDir Path work) throws IOException {
        CompileCache cache = new CompileCache(false, root.toString(), 10);
        assertNull(cache.key(List.of("g++"), "code"));
        Files.writeString(work.resolve("a"), "x");
        cache.store(null, work, null);
        assertFalse(cache.materialize(null, Files.createTempDirectory(root, "w-")));
    }

    @Test
    void pruneKeepsOnlyMaxEntries(@TempDir Path root, @TempDir Path work) throws IOException {
        CompileCache cache = new CompileCache(true, root.toString(), 2);
        for (int i = 0; i < 4; i++) {
            Path dir = Files.createDirectories(work.resolve("case-" + i));
            Files.writeString(dir.resolve("bin"), "artifact-" + i);
            String key = cache.key(List.of("g++"), "code-" + i);
            cache.store(key, dir, null);
            // 保证修改时间递增，便于按最近使用裁剪
            Files.setLastModifiedTime(root.resolve(key),
                    java.nio.file.attribute.FileTime.fromMillis(1000L + i * 1000L));
        }
        long dirs;
        try (var stream = Files.list(root)) {
            dirs = stream.filter(Files::isDirectory).count();
        }
        assertTrue(dirs <= 2, "缓存目录数应不超过上限，实际=" + dirs);
    }
}
