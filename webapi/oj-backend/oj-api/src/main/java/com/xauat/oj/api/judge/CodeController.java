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

@RestController
@RequestMapping("/code")
public class CodeController {
    private final CurrentUser currentUser;
    private final RestClient judge0;
    private final LocalCodeRunner localRunner;
    private final boolean enabled;
    private final boolean localEnabled;

    public CodeController(CurrentUser currentUser, RestClient.Builder builder, LocalCodeRunner localRunner,
                          @Value("${oj.judge0.url}") String url,
                          @Value("${oj.judge0.enabled:false}") boolean enabled,
                          @Value("${oj.judge.local-enabled:false}") boolean localEnabled) {
        this.currentUser = currentUser; this.judge0 = builder.baseUrl(url).build(); this.localRunner = localRunner;
        this.enabled = enabled; this.localEnabled = localEnabled;
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody RunRequest request) {
        currentUser.require(authorization);
        return execute(request);
    }

    @PostMapping("/run/public")
    public ResponseEntity<?> publicRun(@Valid @RequestBody RunRequest request) { return execute(request); }

    private ResponseEntity<?> execute(RunRequest request) {
        if (localEnabled && !enabled) {
            int timeout = 10;
            return ResponseEntity.ok(localRunner.asResponse(localRunner.run(request.code(), request.language(), request.stdin(), timeout)));
        }
        if (!enabled) return ResponseEntity.status(503).body(Map.of("error", "Judge0 服务未启用"));
        try {
            Map<String, Object> payload = Map.of("source_code", request.code(), "language_id", languageId(request.language()), "stdin", request.stdin() == null ? "" : request.stdin());
            Object result = judge0.post().uri("/submissions?wait=true").body(payload).retrieve().body(Object.class);
            return ResponseEntity.ok(result);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(503).body(Map.of("error", "Judge0 服务不可用"));
        }
    }

    private int languageId(String language) { return switch (language.toLowerCase()) { case "c", "c11" -> 50; case "cpp", "c++" -> 54; case "java" -> 62; case "python", "python3" -> 71; case "go" -> 60; case "javascript", "js" -> 63; default -> throw new IllegalArgumentException("不支持的编程语言"); }; }
    public record RunRequest(@NotBlank @jakarta.validation.constraints.Size(max = 131072) String code,
                             @NotBlank @jakarta.validation.constraints.Size(max = 50) String language,
                             @jakarta.validation.constraints.Size(max = 1048576) String stdin) {}
}
