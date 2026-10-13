package com.xauat.oj.api.judge;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.infrastructure.judge.LocalCodeRunner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;

/**
 * 在线代码运行接口（{@code /code}）：非判题的“试运行”能力。
 *
 * <p>支持两种后端：Judge0（HTTP 服务）或本地 LocalCodeRunner（仅当其启用且 Judge0 关闭）。
 * 运行不落库、不判分，仅回显执行结果。</p>
 */
@RestController
@RequestMapping("/code")
public class CodeController {
    private final CurrentUser currentUser;
    private final RestClient judge0;
    private final LocalCodeRunner localRunner;
    private final boolean enabled;
    private final boolean localEnabled;

    public CodeController(CurrentUser currentUser, RestClient.Builder builder, LocalCodeRunner localRunner,
            @Value("${oj.judge0.url}")
            String url,
            @Value("${oj.judge0.enabled:false}")
            boolean enabled,
            @Value("${oj.judge.local-enabled:false}")
            boolean localEnabled) {
        this.currentUser = currentUser;
        this.judge0 = builder.baseUrl(url).build();
        this.localRunner = localRunner;
        this.enabled = enabled;
        this.localEnabled = localEnabled;
    }

    /**
     * 运行代码（需登录）。
     *
     * @param authorization 认证头
     * @param request       代码、语言与可选标准输入
     * @return 运行结果；后端不可用返回 503
     */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestHeader(value = "Authorization", required = false)
    String authorization,
            @Valid @RequestBody
            RunRequest request) {
        currentUser.require(authorization);
        return execute(request);
    }

    /**
     * 匿名运行代码（公开端点，无需登录）。
     *
     * @param request 代码、语言与可选标准输入
     * @return 运行结果；后端不可用返回 503
     */
    @PostMapping("/run/public")
    public ResponseEntity<?> publicRun(@Valid @RequestBody
    RunRequest request) {
        return execute(request);
    }

    /**
     * 统一执行入口：按配置选择本地运行器或 Judge0。
     *
     * @param request 运行请求
     * @return 运行结果；均未启用或远程失败返回 503
     */
    private ResponseEntity<?> execute(RunRequest request) {
        // 本地运行器仅在启用且 Judge0 关闭时作为兜底，超时固定 10 秒。
        if (localEnabled && !enabled) {
            int timeout = 10;
            return ResponseEntity.ok(localRunner
                    .asResponse(localRunner.run(request.code(), request.language(), request.stdin(), timeout)));
        }
        if (!enabled)
            return ResponseEntity.status(503).body(Map.of("error", "Judge0 服务未启用"));
        try {
            // wait=true 同步等待判题结果，简化前端调用。
            Map<String, Object> payload = Map.of("source_code", request.code(), "language_id",
                    languageId(request.language()), "stdin", request.stdin() == null ? "" : request.stdin());
            Object result = judge0.post().uri("/submissions?wait=true").body(payload).retrieve().body(Object.class);
            return ResponseEntity.ok(result);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(Map.of("error", "Judge0 服务不可用"));
        }
    }

    /** 将语言名映射为 Judge0 的 language_id，未知语言抛出异常。 */
    private int languageId(String language) {
        return switch (language.toLowerCase()) {
            case "c", "c11" -> 50;
            case "cpp", "c++" -> 54;
            case "java" -> 62;
            case "python", "python3" -> 71;
            case "go" -> 60;
            case "javascript", "js" -> 63;
            default -> throw new IllegalArgumentException("不支持的编程语言");
        };
    }
    /** 代码运行请求：源码、语言与可选标准输入（均有大小上限）。 */
    public record RunRequest(@NotBlank @jakarta.validation.constraints.Size(max = 131072)
    String code,
            @NotBlank @jakarta.validation.constraints.Size(max = 50)
            String language,
            @jakarta.validation.constraints.Size(max = 1048576)
            String stdin) {
    }
}
