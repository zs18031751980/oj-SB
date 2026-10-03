package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 回收本应用标记且已超过硬截止时间的沙箱容器，避免 Worker 崩溃后容器泄漏。
 * 仅清理带 io.letcoding.sandbox=1 标签的容器。
 */
@Component
@ConditionalOnProperty(name = "oj.judge.executor", havingValue = "sandbox")
public class SandboxReaper {
    private static final Logger log = LoggerFactory.getLogger(SandboxReaper.class);
    private final ObjectMapper mapper;

    public SandboxReaper(ObjectMapper mapper) { this.mapper = mapper; }

    @Scheduled(fixedDelayString = "${oj.sandbox.reaper-ms:60000}", initialDelayString = "${oj.sandbox.reaper-initial-ms:15000}")
    public void reap() {
        long now = System.currentTimeMillis() / 1000;
        List<String> ids = run("docker", "ps", "-aq", "--filter", "label=io.letcoding.sandbox=1");
        if (ids == null) return;
        for (String id : ids.size() > 10 ? ids.subList(ids.size() - 10, ids.size()) : ids) {
            if (!id.matches("[0-9a-f]{12,64}")) continue;
            List<String> labels = run("docker", "inspect", "--format", "{{json .Config.Labels}}", id);
            if (labels == null || labels.isEmpty()) continue;
            try {
                JsonNode node = mapper.readTree(labels.get(0));
                long expires = node.path("io.letcoding.expires").asLong(0);
                if (expires > 0 && expires <= now) {
                    run("docker", "rm", "-f", id);
                    log.warn("回收过期沙箱容器 {}", id);
                }
            } catch (Exception ignored) { }
        }
    }

    private List<String> run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); return null; }
            if (process.exitValue() != 0) return null;
            return output.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
}
