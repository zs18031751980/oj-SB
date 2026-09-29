package com.xauat.oj.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.problem.repository.TestcaseRepository;
import com.xauat.oj.core.contest.repository.JudgementRepository;
import com.xauat.oj.core.contest.domain.Judgement;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 生产环境通过 oj.judge.enabled=true 启用 Judge0 适配器；未启用时由 DisabledJudgeExecutor 保留任务。
 */
@Component
@ConditionalOnProperty(name = "oj.judge.enabled", havingValue = "true")
public class ConfiguredJudgeExecutor implements JudgeExecutor {
    private final SubmissionRepository submissions;
    private final ContestSubmissionRepository contestSubmissions;
    private final TestcaseRepository testcases;
    private final JudgementRepository judgements;
    private final ContestTestcaseRepository contestTestcases;
    private final ContestProblemRepository contestProblems;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String judgeUrl;
    private final Map<String, Integer> languageIds = new HashMap<>();

    public ConfiguredJudgeExecutor(SubmissionRepository submissions, ContestSubmissionRepository contestSubmissions, TestcaseRepository testcases, JudgementRepository judgements, ContestTestcaseRepository contestTestcases, ContestProblemRepository contestProblems, ObjectMapper mapper,
                                   @Value("${oj.judge.url}") String judgeUrl,
                                   @Value("${oj.judge.language-ids:cpp=54,python=71,java=62,go=60,javascript=63}") String languageConfig) {
        this.submissions = submissions; this.contestSubmissions = contestSubmissions; this.testcases = testcases; this.judgements = judgements; this.contestTestcases = contestTestcases; this.contestProblems = contestProblems; this.mapper = mapper; this.judgeUrl = judgeUrl;
        for (String item : languageConfig.split(",")) { String[] parts = item.split("=", 2); if (parts.length == 2) languageIds.put(parts[0], Integer.valueOf(parts[1])); }
    }

    @Override
    public void execute(String jobId) {
        var regular = submissions.findByJobId(jobId);
        if (regular.isPresent()) { var item = regular.get(); item.markRunning(); submissions.save(item); var cases = testcases.findByProblemIdOrderBySortOrderAscIdAsc(item.getProblemId()); JudgeResult result = cases.isEmpty() ? judge(item.getCode(), item.getLanguage(), "", "") : judgeCases(item.getCode(), item.getLanguage(), cases); item.recordResult(result.verdict(), result.timeMs(), result.memoryKb(), result.details(), "Accepted".equals(result.verdict()) ? null : 0); submissions.save(item); return; }
        var contest = contestSubmissions.findByJobId(jobId).orElseThrow(() -> new IllegalArgumentException("未知判题任务: " + jobId));
        contest.markRunning("judge0-worker"); contestSubmissions.save(contest); var cases = contestTestcases.findByContestProblemIdOrderBySortOrderAscIdAsc(contest.getContestProblemId()); JudgeResult result = cases.isEmpty() ? judge(contest.getCode(), contest.getLanguage(), "", "") : judgeContestCases(contest.getCode(), contest.getLanguage(), cases); int passed = result.passed(); int total = cases.isEmpty() ? 1 : cases.size(); int updated = contestSubmissions.updateResultIfCurrentAttempt(contest.getId(), contest.getAttemptId(), result.verdict(), result.timeMs(), result.memoryKb() == null ? null : result.memoryKb().longValue(), passed, total, result.details()); if (updated == 1) { String digest = contestProblems.findById(contest.getContestProblemId()).map(p -> p.getPackageDigest()).orElse(null); judgements.save(Judgement.of(contest, contest.getAttemptId(), result.verdict(), result.details(), digest, null)); }
    }

    private JudgeResult judgeCases(String sourceCode, String language, java.util.List<com.xauat.oj.core.problem.domain.Testcase> cases) {
        java.util.List<String> details = new java.util.ArrayList<>(); JudgeResult last = new JudgeResult("Accepted", 0, 0, "{}", 0);
        for (var testcase : cases) { last = judge(sourceCode, language, testcase.getInputData(), testcase.getOutputData()); details.add(last.details()); if (!"Accepted".equals(last.verdict())) return new JudgeResult(last.verdict(), last.timeMs(), last.memoryKb(), json(details), details.size()-1); }
        return new JudgeResult("Accepted", last.timeMs(), last.memoryKb(), json(details), cases.size());
    }

    private JudgeResult judgeContestCases(String sourceCode, String language, java.util.List<com.xauat.oj.core.contest.domain.ContestTestcase> cases) { java.util.List<String> details = new java.util.ArrayList<>(); JudgeResult last = new JudgeResult("Accepted", 0, 0, "[]", 0); String checker = contestProblems.findById(cases.get(0).getContestProblemId()).map(p -> checkerName(p.getCheckerConfig())).orElse("text"); for (var testcase : cases) { last = judge(sourceCode, language, testcase.getInputData(), testcase.getExpectedOutput(), checker); details.add(last.details()); if (!"Accepted".equals(last.verdict())) return new JudgeResult(last.verdict(), last.timeMs(), last.memoryKb(), json(details), details.size()-1); } return new JudgeResult("Accepted", last.timeMs(), last.memoryKb(), json(details), cases.size()); }

    private JudgeResult judge(String sourceCode, String language, String stdin, String expectedOutput) { return judge(sourceCode, language, stdin, expectedOutput, "exact"); }
    private JudgeResult judge(String sourceCode, String language, String stdin, String expectedOutput, String checker) {
        Integer languageId = languageIds.get(language.toLowerCase()); if (languageId == null) throw new IllegalArgumentException("不支持的语言: " + language);
        try {
            if ("custom".equals(checker)) return new JudgeResult("Checker Unavailable", 0, 0, "{\"error\":\"custom checker requires sandbox configuration\"}", 0);
            Map<String,Object> request = new HashMap<>(); request.put("source_code", sourceCode); request.put("language_id", languageId); request.put("stdin", stdin); if (("exact".equals(checker) || "text".equals(checker)) && expectedOutput != null && !expectedOutput.isBlank()) request.put("expected_output", expectedOutput);
            String body = mapper.writeValueAsString(request);
            HttpRequest create = HttpRequest.newBuilder(URI.create(judgeUrl + "/submissions?base64_encoded=false&wait=false")).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonNode created = mapper.readTree(client.send(create, HttpResponse.BodyHandlers.ofString()).body()); String token = created.path("token").asText();
            if (token.isBlank()) throw new IllegalStateException("Judge0 未返回 token");
            for (int i = 0; i < 60; i++) { Thread.sleep(500); HttpRequest poll = HttpRequest.newBuilder(URI.create(judgeUrl + "/submissions/" + token + "?base64_encoded=false")).timeout(Duration.ofSeconds(10)).GET().build(); JsonNode result = mapper.readTree(client.send(poll, HttpResponse.BodyHandlers.ofString()).body()); int status = result.path("status").path("id").asInt(0); if (status >= 3) { String verdict = status == 3 ? "Accepted" : result.path("status").path("description").asText("Judgement Failed"); if ("Accepted".equals(verdict) && !("exact".equals(checker) || "text".equals(checker)) && !matches(result.path("stdout").asText(""), expectedOutput, checker)) verdict = "Wrong Answer"; return new JudgeResult(verdict, result.path("time").asInt(0), result.path("memory").asInt(0), mapper.writeValueAsString(result), "Accepted".equals(verdict) ? 1 : 0); } }
            return new JudgeResult("Time Limit Exceeded", 0, 0, "{}", 0);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("判题被中断", exception); }
        catch (Exception exception) { throw new IllegalStateException("Judge0 调用失败", exception); }
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { return "[]"; } }
    private String checkerName(String config) { try { String name = mapper.readTree(config == null ? "{}" : config).path("checker").asText("text").toLowerCase(); return switch (name) { case "tokens", "float", "custom", "exact", "text" -> name; default -> "text"; }; } catch (Exception exception) { return "text"; } }
    private boolean matches(String actual, String expected, String checker) { if (expected == null) return false; if ("tokens".equals(checker)) return java.util.Arrays.equals(actual.trim().split("\\s+"), expected.trim().split("\\s+")); if ("float".equals(checker)) { try { String[] a = actual.trim().split("\\s+"); String[] e = expected.trim().split("\\s+"); if (a.length != e.length) return false; for (int i = 0; i < a.length; i++) if (Math.abs(Double.parseDouble(a[i]) - Double.parseDouble(e[i])) > 1e-6) return false; return true; } catch (Exception exception) { return false; } } return actual.trim().equals(expected.trim()); }
    private record JudgeResult(String verdict, Integer timeMs, Integer memoryKb, String details, int passed) {}
}
