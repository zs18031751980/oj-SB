package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.ContestPackage;
import com.xauat.oj.core.contest.repository.ContestPackageRepository;
import com.xauat.oj.infrastructure.judge.Checker;
import com.xauat.oj.infrastructure.judge.ExecutionClient;
import com.xauat.oj.infrastructure.judge.Judge0Client;
import com.xauat.oj.infrastructure.judge.JudgeVerdict;
import org.springframework.stereotype.Component;

/**
 * 题包验证：参考答案必须通过全部测试点；输入验证器必须接受测试数据；已知错误程序必须被拒绝。
 * 任一不满足则题包标记 INVALID，绝不激活。
 */
@Component
public class PackageValidator {
    private final ContestPackageRepository packages;
    private final ExecutionClient judge0;
    private final Checker checker;
    private final ObjectMapper mapper;

    public PackageValidator(ContestPackageRepository packages, ExecutionClient judge0, Checker checker, ObjectMapper mapper) {
        this.packages = packages; this.judge0 = judge0; this.checker = checker; this.mapper = mapper;
    }

    public void validate(String digest) {
        ContestPackage pkg = packages.findById(digest).orElse(null);
        if (pkg == null || "VALID".equals(pkg.getValidationState())) return;
        pkg.markValidating();
        packages.save(pkg);
        JsonNode data;
        try { data = mapper.readTree(pkg.getPayload()); }
        catch (Exception exception) { pkg.markInvalid("题包负载损坏"); packages.save(pkg); return; }
        try {
            JsonNode cases = data.path("cases");
            if (!cases.isArray() || cases.isEmpty()) throw new IllegalArgumentException("题包没有测试数据");
            String checkerConfig = data.path("checker_config").toString();
            checkReference(data, cases, checkerConfig);
            checkValidator(data, cases);
            checkKnownWrong(data, cases, checkerConfig);
            pkg.markValid();
        } catch (IllegalArgumentException exception) {
            pkg.markInvalid(exception.getMessage());
        }
        // 基础设施异常向上抛出，保留任务供恢复，不计为 INVALID。
        packages.save(pkg);
    }

    private void checkReference(JsonNode data, JsonNode cases, String checkerConfig) {
        String code = data.path("reference").asText();
        String language = data.path("language").asText("cpp");
        for (int i = 0; i < cases.size(); i++) {
            JsonNode testcase = cases.get(i);
            Judge0Client.Result result = judge0.run(code, language, testcase.path("input_data").asText(), 60);
            if (!JudgeVerdict.ACCEPTED.equals(JudgeVerdict.fromStatus(result.statusId(), result.description()))
                    || !checker.check(checkerConfig, result.stdout(), testcase.path("expected_output").asText(), testcase.path("input_data").asText())) {
                throw new IllegalArgumentException("标准程序未通过测试点 " + (i + 1));
            }
        }
    }

    private void checkValidator(JsonNode data, JsonNode cases) {
        JsonNode validator = data.path("validator");
        if (validator.isMissingNode() || validator.isNull()) return;
        String code = validator.path("code").asText("");
        String language = validator.path("language").asText("cpp");
        if (code.isBlank()) return;
        for (int i = 0; i < cases.size(); i++) {
            Judge0Client.Result result = judge0.run(code, language, cases.get(i).path("input_data").asText(), 20);
            Integer exit = result.exitCode();
            if (exit == null || exit != 0) throw new IllegalArgumentException("输入验证器拒绝测试数据（第 " + (i + 1) + " 组）");
        }
    }

    private void checkKnownWrong(JsonNode data, JsonNode cases, String checkerConfig) {
        JsonNode wrongList = data.path("known_wrong");
        if (!wrongList.isArray()) return;
        for (JsonNode wrong : wrongList) {
            String code = wrong.path("code").asText("");
            String language = wrong.path("language").asText("cpp");
            if (code.isBlank()) continue;
            boolean rejected = false;
            for (JsonNode testcase : cases) {
                Judge0Client.Result result = judge0.run(code, language, testcase.path("input_data").asText(), 60);
                boolean accepted = JudgeVerdict.ACCEPTED.equals(JudgeVerdict.fromStatus(result.statusId(), result.description()))
                        && checker.check(checkerConfig, result.stdout(), testcase.path("expected_output").asText(), testcase.path("input_data").asText());
                if (!accepted) { rejected = true; break; }
            }
            if (!rejected) throw new IllegalArgumentException("已知错误程序未被拒绝");
        }
    }
}
