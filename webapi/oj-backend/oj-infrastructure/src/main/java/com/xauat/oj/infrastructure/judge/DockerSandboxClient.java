package com.xauat.oj.infrastructure.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 仅 Worker 主机使用的 Docker CLI 桥：把执行请求交给沙箱镜像内的 execution_runtime.py。
 * 安全边界与原后端一致：network=none、只读根、cap-drop、no-new-privileges、内存/PID/输出限制。
 */
@Component
public class DockerSandboxClient {
    public record Result(String stdout, String stderr, int returncode, boolean timedOut,
                         boolean outputExceeded, boolean memoryExceeded, long wallMs, long cpuMs, long memoryBytes) {}

    private final ObjectMapper mapper;
    private final String image;
    private final String backend;
    private final long uid;
    private final long gid;

    public DockerSandboxClient(ObjectMapper mapper,
                               @Value("${oj.judge.sandbox.image:letcoding-sandbox:local}") String image,
                               @Value("${oj.judge.sandbox.backend:docker}") String backend) {
        this.mapper = mapper; this.image = image; this.backend = backend;
        this.uid = currentId("-u"); this.gid = currentId("-g");
    }

    public Result execute(List<String> command, String stdin, double timeoutSeconds, int memoryMb, Path workdir, boolean readonly, long outputLimit) {
        if (!"docker".equals(backend)) throw new UnsupportedOperationException("sandbox backend 仅支持 docker");
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("空执行命令");
        Path resolved = workdir.toAbsolutePath().normalize();
        List<String> translated = new ArrayList<>();
        for (String part : command) translated.add(part.replace(resolved.toString(), "/work"));
        int memory = Math.max(16, Math.min(memoryMb <= 0 ? 256 : memoryMb, 2048));
        String name = "letcoding-job-" + UUID.randomUUID().toString().replace("-", "");
        List<String> options = new ArrayList<>(List.of("docker", "run", "--name", name, "--network=none", "--read-only",
                "--cap-drop=ALL", "--cap-add=SETUID", "--cap-add=SETGID", "--cap-add=KILL",
                "--security-opt=no-new-privileges", "--pids-limit=128",
                "--memory=" + memory + "m", "--memory-swap=" + memory + "m", "--cpus=1",
                "--ulimit", "nofile=128:128", "--ulimit", "fsize=67108864:67108864",
                "--user", "0:0", "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=256m",
                "--mount", "type=bind,src=" + resolved + ",dst=/work" + (readonly ? ",readonly" : ""),
                "--workdir", "/", "--label", "io.letcoding.sandbox=1",
                "--label", "io.letcoding.expires=" + (Instant.now().getEpochSecond() + (long) timeoutSeconds + 90),
                "--log-driver=none", "-i", image, "python3", "/opt/execution_runtime.py"));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("command", translated);
            payload.put("stdin", stdin == null ? "" : stdin);
            payload.put("timeout", timeoutSeconds);
            payload.put("cpu_timeout", timeoutSeconds);
            payload.put("output_limit", outputLimit);
            payload.put("uid", uid);
            payload.put("gid", gid);
            Process process = new ProcessBuilder(options).redirectErrorStream(false).start();
            try (OutputStream out = process.getOutputStream()) {
                out.write(mapper.writeValueAsBytes(payload));
            }
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outReader = drain(process.getInputStream(), stdout);
            Thread errReader = drain(process.getErrorStream(), stderr);
            boolean finished = process.waitFor((long) timeoutSeconds + 15, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); throw new IllegalStateException("执行容器启动或监督超时"); }
            outReader.join(2000); errReader.join(2000);
            if (process.exitValue() != 0) throw new IllegalStateException("执行器异常: " + stderr.toString(StandardCharsets.UTF_8).trim());
            return parse(stdout.toString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("沙箱执行失败", exception);
        } finally {
            cleanup(name);
        }
    }

    private Result parse(String raw) {
        String line = raw.strip();
        if (line.isEmpty()) throw new IllegalStateException("执行器未返回结果");
        int newline = line.lastIndexOf('\n');
        if (newline >= 0) line = line.substring(newline + 1);
        try {
            JsonNode node = mapper.readTree(line);
            return new Result(node.path("stdout").asText(""), node.path("stderr").asText(""),
                    node.path("returncode").asInt(0), node.path("timed_out").asBoolean(false),
                    node.path("output_exceeded").asBoolean(false), node.path("memory_exceeded").asBoolean(false),
                    node.path("wall_ms").asLong(0), node.path("cpu_ms").asLong(0), node.path("memory_bytes").asLong(0));
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析执行器结果", exception);
        }
    }

    private Thread drain(InputStream input, ByteArrayOutputStream sink) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try { int read; while ((read = input.read(buffer)) >= 0) sink.write(buffer, 0, read); }
            catch (IOException ignored) { }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void cleanup(String name) {
        try {
            Process process = new ProcessBuilder("docker", "rm", "-f", name).redirectErrorStream(true).start();
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private long currentId(String flag) {
        try {
            Process process = new ProcessBuilder("id", flag).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String value = reader.readLine();
                process.waitFor(5, TimeUnit.SECONDS);
                return value == null ? 1000 : Long.parseLong(value.trim());
            }
        } catch (Exception exception) {
            return 1000;
        }
    }
}
