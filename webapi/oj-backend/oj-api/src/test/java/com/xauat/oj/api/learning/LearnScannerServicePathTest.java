package com.xauat.oj.api.learning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 学习资源路径安全回归：拒绝 ..、绝对路径与越界访问，允许根目录内的正常文件。
 */
class LearnScannerServicePathTest {

    @Test
    void rejectsTraversalAndAbsolutePaths(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("a.md"), "# A");
        Files.writeString(root.resolve("sub/b.md"), "# B");
        Path outside = root.getParent().resolve("secret-" + System.nanoTime() + ".md");
        Files.writeString(outside, "# secret");
        try {
            LearnScannerService scanner = new LearnScannerService(root.toString());

            assertTrue(scanner.readMarkdown("a.md").isPresent());
            assertTrue(scanner.readMarkdown("sub/b.md").isPresent());
            // 正常相对路径中的 .. 若仍在根内应允许
            assertTrue(scanner.readMarkdown("sub/../a.md").isPresent());

            // 越界与绝对路径必须拒绝
            assertTrue(scanner.readMarkdown("../" + outside.getFileName()).isEmpty());
            assertTrue(scanner.readMarkdown("sub/../../etc/passwd").isEmpty());
            assertTrue(scanner.readMarkdown("/etc/passwd").isEmpty());
            assertTrue(scanner.resolveAsset("../" + outside.getFileName()).isEmpty());
            assertTrue(scanner.resolveAsset("/etc/passwd").isEmpty());
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
