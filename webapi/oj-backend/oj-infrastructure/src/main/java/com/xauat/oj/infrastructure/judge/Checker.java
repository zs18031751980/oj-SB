package com.xauat.oj.infrastructure.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一输出判定：text/exact/tokens/float 由本地策略处理；custom 通过 Judge0 运行检查器程序，
 * 约定 stdin 收到 {input,actual,expected} JSON，退出码 0 通过、1 拒绝、其余为基础设施错误。
 */
@Component
public class Checker {
    private final ExecutionClient judge0;
    private final ObjectMapper mapper;

    public Checker(ExecutionClient judge0, ObjectMapper mapper) { this.judge0 = judge0; this.mapper = mapper; }

    public static final class CheckerUnavailableException extends RuntimeException {
        public CheckerUnavailableException(String message) { super(message); }
    }

    public boolean check(String configJson, String actual, String expected, String input) {
        JsonNode config = parse(configJson);
        String mode = config.path("checker").asText("text").toLowerCase();
        if ("custom".equals(mode)) return runCustom(config, actual, expected, input);
        if (!OutputChecker.supported(mode)) throw new CheckerUnavailableException("不支持的 checker: " + mode);
        double absolute = config.path("absolute_tolerance").asDouble(1e-6);
        double relative = config.path("relative_tolerance").asDouble(1e-6);
        return OutputChecker.matches(mode, actual, expected, absolute, relative);
    }

    public static boolean supportedName(String name) {
        return name != null && switch (name) {
            case "text", "exact", "tokens", "float", "custom" -> true;
            default -> false;
        };
    }

    private boolean runCustom(JsonNode config, String actual, String expected, String input) {
        String language = config.path("language").asText("");
        String code = config.path("code").asText("");
        if (code.isBlank() || !(language.equals("python") || language.equals("cpp"))) {
            throw new CheckerUnavailableException("custom checker 配置无效");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("input", input == null ? "" : input);
            payload.put("actual", actual == null ? "" : actual);
            payload.put("expected", expected == null ? "" : expected);
            Judge0Client.Result result = judge0.run(code, language, mapper.writeValueAsString(payload), 40);
            Integer exit = result.exitCode();
            if (exit == null) {
                if (result.statusId() == 6) throw new CheckerUnavailableException("检查器编译失败");
                throw new CheckerUnavailableException("检查器执行失败");
            }
            if (exit == 0) return true;
            if (exit == 1) return false;
            throw new CheckerUnavailableException("检查器执行失败");
        } catch (CheckerUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CheckerUnavailableException("检查器执行失败");
        }
    }

    private JsonNode parse(String configJson) {
        try { return mapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson); }
        catch (Exception exception) { throw new CheckerUnavailableException("checker 配置无效"); }
    }
}
