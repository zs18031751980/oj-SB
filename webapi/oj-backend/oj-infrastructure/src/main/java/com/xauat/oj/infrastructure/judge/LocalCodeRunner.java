package com.xauat.oj.infrastructure.judge;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 仅供本地开发使用的执行后端（无 Docker/Judge0 时）。直接在主机上编译并运行用户代码，
 * 未做沙箱隔离，必须由 {@code OJ_ALLOW_LOCAL_JUDGE=true} 显式开启，切勿用于生产。
 */
@Component
public class LocalCodeRunner {
    public record Result(String stdout, String stderr, Integer exitCode, boolean timedOut, long timeMs, String message) {}

    public Result run(String code, String language, String stdin, int timeoutSeconds) {
        String lang = language == null ? "cpp" : language.toLowerCase(java.util.Locale.ROOT);
        Path work;
        try { work = Files.createTempDirectory("oj-local-"); }
        catch (IOException exception) { return new Result("", "", null, false, 0, "无法创建临时目录"); }
        try {
            Language spec = language(lang, work);
            if (spec == null) return new Result("", "", null, false, 0, "不支持的编程语言: " + language);
            String sourceName = spec.sourceName();
            Files.writeString(work.resolve(sourceName), code == null ? "" : code, StandardCharsets.UTF_8);
            if (!spec.compile().isEmpty()) {
                Proc compile = exec(spec.compile(), "", work, 20);
                if (compile.timedOut()) return new Result("", "", null, true, compile.timeMs(), "编译超时");
                if (compile.exitCode() != 0) {
                    String error = compile.stderr().isBlank() ? compile.stdout() : compile.stderr();
                    return new Result("", "", compile.exitCode(), false, compile.timeMs(), error.isBlank() ? "编译错误" : error);
                }
            }
            Proc result = exec(spec.run(), stdin == null ? "" : stdin, work, Math.max(1, timeoutSeconds));
            if (result.timedOut()) return new Result(result.stdout(), result.stderr(), null, true, result.timeMs(), "运行超时");
            return new Result(result.stdout(), result.stderr(), result.exitCode(), false, result.timeMs(), "");
        } catch (IOException exception) {
            return new Result("", "", null, false, 0, "执行失败: " + exception.getMessage());
        } finally {
            deleteRecursively(work);
        }
    }

    private record Language(String sourceName, List<String> compile, List<String> run) {}

    private Language language(String lang, Path work) {
        return switch (lang) {
            case "c", "c11" -> new Language("main.c", List.of("gcc", "-O2", "-o", work.resolve("main").toString(), work.resolve("main.c").toString()), List.of(work.resolve("main").toString()));
            case "cpp", "c++" -> new Language("main.cpp", List.of("g++", "-O2", "-std=c++17", "-o", work.resolve("main").toString(), work.resolve("main.cpp").toString()), List.of(work.resolve("main").toString()));
            case "python", "python3" -> new Language("main.py", List.of(), List.of("python3", work.resolve("main.py").toString()));
            case "java" -> new Language("Main.java", List.of("javac", "-d", work.toString(), work.resolve("Main.java").toString()), List.of("java", "-cp", work.toString(), "Main"));
            case "go" -> new Language("main.go", List.of(), List.of("go", "run", work.resolve("main.go").toString()));
            case "javascript", "js" -> new Language("main.js", List.of(), List.of("node", work.resolve("main.js").toString()));
            default -> null;
        };
    }

    private record Proc(String stdout, String stderr, Integer exitCode, boolean timedOut, long timeMs) {}

    private Proc exec(List<String> command, String stdin, Path work, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
            builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            Process process = builder.start();
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(stdin);
            } catch (IOException ignored) { }
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outReader = drain(process.getInputStream(), stdout, 1024 * 1024);
            Thread errReader = drain(process.getErrorStream(), stderr, 1024 * 1024);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
            outReader.join(1000); errReader.join(1000);
            long elapsed = System.currentTimeMillis() - start;
            return new Proc(stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8),
                    finished ? process.exitValue() : null, !finished, elapsed);
        } catch (IOException exception) {
            return new Proc("", exception.getMessage(), null, false, System.currentTimeMillis() - start);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Proc("", "执行被中断", null, false, System.currentTimeMillis() - start);
        }
    }

    private Thread drain(InputStream input, ByteArrayOutputStream sink, int limit) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream in = input) {
                int read; int total = 0;
                while (total < limit && (read = in.read(buffer)) >= 0) { sink.write(buffer, 0, Math.min(read, limit - total)); total += read; }
            } catch (IOException ignored) { }
        });
        thread.setDaemon(true); thread.start();
        return thread;
    }

    private void deleteRecursively(Path path) {
        if (path == null) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }

    public Map<String, Object> asResponse(Result result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stdout", result.stdout());
        body.put("stderr", result.stderr());
        body.put("exit_code", result.exitCode());
        body.put("timed_out", result.timedOut());
        body.put("time", result.timeMs());
        body.put("memory", 0);
        body.put("message", result.message());
        body.put("status", result.stderr().isBlank() || result.exitCode() == null ? "执行完成" : "运行错误");
        return body;
    }
}
