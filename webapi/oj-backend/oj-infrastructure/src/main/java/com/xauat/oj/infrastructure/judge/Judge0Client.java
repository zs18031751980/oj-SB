package com.xauat.oj.infrastructure.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Judge0 CE HTTP 客户端。只负责一次执行与取回原始结果，判定逻辑由调用方使用检查器完成，
 * 以便支持 tokens/float 等自定义比较与 OI 部分分。
 */
@Component
@ConditionalOnProperty(name = "oj.judge.executor", havingValue = "judge0", matchIfMissing = true)
public class Judge0Client implements ExecutionClient {
    public record Result(int statusId, String description, String stdout, String stderr,
                         Integer timeMs, Integer memoryKb, String compileOutput, Integer exitCode) {}

    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final Map<String, Integer> languageIds = new HashMap<>();

    public Judge0Client(ObjectMapper mapper,
                        @Value("${oj.judge.url:${oj.judge0.url:http://localhost:2358}}") String baseUrl,
                        @Value("${oj.judge.language-ids:${oj.judge0.language-ids:cpp=54,python=71,java=62,go=60,javascript=63,c=50}}") String languageConfig) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        for (String item : languageConfig.split(",")) {
            String[] parts = item.split("=", 2);
            if (parts.length == 2) languageIds.put(parts[0].trim().toLowerCase(), Integer.valueOf(parts[1].trim()));
        }
    }

    public Integer languageId(String language) {
        return language == null ? null : languageIds.get(language.toLowerCase());
    }

    public Result run(String sourceCode, String language, String stdin, int pollAttempts) {
        Integer languageId = languageId(language);
        if (languageId == null) throw new IllegalArgumentException("不支持的语言: " + language);
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("source_code", sourceCode);
            request.put("language_id", languageId);
            request.put("stdin", stdin == null ? "" : stdin);
            String body = mapper.writeValueAsString(request);
            HttpRequest create = HttpRequest.newBuilder(URI.create(baseUrl + "/submissions?base64_encoded=false&wait=false"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonNode created = mapper.readTree(client.send(create, HttpResponse.BodyHandlers.ofString()).body());
            String token = created.path("token").asText();
            if (token.isBlank()) throw new IllegalStateException("Judge0 未返回 token");
            for (int i = 0; i < pollAttempts; i++) {
                Thread.sleep(500);
                HttpRequest poll = HttpRequest.newBuilder(URI.create(baseUrl + "/submissions/" + token + "?base64_encoded=false"))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                JsonNode result = mapper.readTree(client.send(poll, HttpResponse.BodyHandlers.ofString()).body());
                int status = result.path("status").path("id").asInt(0);
                if (status >= 3) {
                    return new Result(status, result.path("status").path("description").asText("Judgement Failed"),
                            result.path("stdout").asText(""), result.path("stderr").asText(""),
                            result.path("time").isMissingNode() || result.path("time").isNull() ? null : result.path("time").asInt(),
                            result.path("memory").isMissingNode() || result.path("memory").isNull() ? null : result.path("memory").asInt(),
                            result.path("compile_output").asText(""),
                            result.path("exit_code").isMissingNode() || result.path("exit_code").isNull() ? null : result.path("exit_code").asInt());
                }
            }
            return new Result(5, "Time Limit Exceeded", "", "", null, null, "", null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("判题被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Judge0 调用失败", exception);
        }
    }
}
