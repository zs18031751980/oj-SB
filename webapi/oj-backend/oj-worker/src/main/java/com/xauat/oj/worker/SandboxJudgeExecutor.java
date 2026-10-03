package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.domain.ContestSubmission;
import com.xauat.oj.core.contest.domain.Judgement;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.JudgementRepository;
import com.xauat.oj.core.problem.repository.TestcaseRepository;
import com.xauat.oj.core.submission.domain.Submission;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.infrastructure.judge.Checker;
import com.xauat.oj.infrastructure.judge.DockerSandboxClient;
import com.xauat.oj.infrastructure.judge.JudgeVerdict;
import com.xauat.oj.infrastructure.judge.OutputChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 使用现有 Docker 沙箱（execution_runtime.py）判题。一次提交只编译一次，随后按测试点只读运行。
 * 通过 oj.judge.executor=sandbox 且 oj.judge.enabled=true 启用。
 */
@Component
@ConditionalOnExpression("'${oj.judge.executor:judge0}' == 'sandbox' and ${oj.judge.enabled:false}")
public class SandboxJudgeExecutor implements JudgeExecutor {
    private final SubmissionRepository submissions;
    private final ContestSubmissionRepository contestSubmissions;
    private final TestcaseRepository testcases;
    private final ContestTestcaseRepository contestTestcases;
    private final ContestProblemRepository contestProblems;
    private final ContestRepository contests;
    private final JudgementRepository judgements;
    private final DockerSandboxClient sandbox;
    private final Checker checker;
    private final com.xauat.oj.infrastructure.queue.JudgeQueue queue;
    private final ObjectMapper mapper;

    public SandboxJudgeExecutor(SubmissionRepository submissions, ContestSubmissionRepository contestSubmissions,
                                TestcaseRepository testcases, ContestTestcaseRepository contestTestcases,
                                ContestProblemRepository contestProblems, ContestRepository contests,
                                JudgementRepository judgements, DockerSandboxClient sandbox, Checker checker,
                                com.xauat.oj.infrastructure.queue.JudgeQueue queue, ObjectMapper mapper) {
        this.submissions = submissions; this.contestSubmissions = contestSubmissions; this.testcases = testcases;
        this.contestTestcases = contestTestcases; this.contestProblems = contestProblems; this.contests = contests;
        this.judgements = judgements; this.sandbox = sandbox; this.checker = checker; this.queue = queue; this.mapper = mapper;
    }

    private record Language(String sourceName, List<String> compile, List<String> run) {}

    @Override
    public void execute(String jobId) {
        var regular = submissions.findByJobId(jobId);
        if (regular.isPresent()) { judgeRegular(regular.get()); return; }
        judgeContest(contestSubmissions.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("未知判题任务: " + jobId)));
    }

    private void judgeRegular(Submission submission) {
        submission.markRunning();
        submissions.save(submission);
        var cases = testcases.findByProblemIdOrderBySortOrderAscIdAsc(submission.getProblemId());
        Path work = null;
        try {
            work = Files.createTempDirectory("oj-sandbox-");
            Language language = language(submission.getLanguage(), work);
            String compileError = compile(language, submission.getCode(), work);
            if (compileError != null) {
                submission.recordResult(JudgeVerdict.COMPILATION_ERROR, 0, 0, "[]", null);
                submissions.save(submission);
                return;
            }
            List<Map<String, Object>> outcomes = new ArrayList<>();
            int passed = 0; int time = 0; int memory = 0; String verdict = JudgeVerdict.ACCEPTED; Integer failIndex = null;
            for (int i = 0; i < cases.size(); i++) {
                var testcase = cases.get(i);
                DockerSandboxClient.Result result = sandbox.execute(language.run(), testcase.getInputData(), 3, 256, work, true, 1024 * 1024);
                String status = verdictOf(result, "exact", null, testcase.getOutputData(), testcase.getInputData());
                boolean ok = JudgeVerdict.ACCEPTED.equals(status);
                outcomes.add(outcome(i, status, ok, result, testcase.getInputData(), testcase.getOutputData()));
                time += (int) result.cpuMs(); memory = (int) Math.max(memory, result.memoryBytes() / 1024);
                if (ok) passed++; else { failIndex = i; verdict = status; break; }
            }
            submission.recordResult(verdict, time, memory, json(outcomes), failIndex);
            submissions.save(submission);
        } catch (IOException exception) {
            throw new IllegalStateException("无法准备沙箱工作目录", exception);
        } finally {
            deleteRecursively(work);
        }
    }

    private void judgeContest(ContestSubmission submission) {
        submission.markRunning("sandbox-worker");
        contestSubmissions.save(submission);
        ContestProblem problem = contestProblems.findById(submission.getContestProblemId()).orElse(null);
        Contest contest = problem == null ? null : contests.findById(problem.getContestId()).orElse(null);
        boolean oi = contest != null && "OI".equalsIgnoreCase(contest.getContestType());
        var cases = contestTestcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(submission.getContestProblemId());
        String checkerConfig = problem == null ? "{}" : problem.getCheckerConfig();
        String slotSubject = "user:" + submission.getUserId();
        String slotToken = "job:" + submission.getJobId();
        if (!queue.acquireExecutionSlot(slotSubject, slotToken, contest != null ? Math.max(1, contest.getActiveSubmissionLimit()) : 3, 120)) {
            throw new IllegalStateException("执行并发槽已满: " + slotSubject);
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("oj-sandbox-");
            Language language = language(submission.getLanguage(), work);
            java.time.LocalDateTime compileStart = java.time.LocalDateTime.now();
            String compileError = compile(language, submission.getCode(), work);
            java.time.LocalDateTime compileEnd = java.time.LocalDateTime.now();
            if (compileError != null) {
                int updated = updateContestResult(submission, problem, contest, JudgeVerdict.COMPILATION_ERROR, 0, 0, 0, cases.size(), "[]");
                if (updated == 1) contestSubmissions.updateMetricsIfCurrentAttempt(submission.getId(), submission.getAttemptId(),
                        compileStart, compileEnd, null, null, 0, null, null, problem == null ? null : problem.getPackageDigest());
                return;
            }
            List<Map<String, Object>> outcomes = new ArrayList<>();
            int passed = 0; long time = 0; long memory = 0; String verdict = JudgeVerdict.ACCEPTED; String firstFailure = null;
            int outputSize = 0; Integer lastExit = null;
            java.time.LocalDateTime executionStart = java.time.LocalDateTime.now();
            for (int i = 0; i < cases.size(); i++) {
                var testcase = cases.get(i);
                DockerSandboxClient.Result result = sandbox.execute(language.run(), testcase.getInputData(), 3, 256, work, true, 1024 * 1024);
                outputSize += result.stdout() == null ? 0 : result.stdout().length();
                lastExit = result.returncode();
                String status = verdictOf(result, "custom-checker", checkerConfig, testcase.getExpectedOutput(), testcase.getInputData());
                boolean ok = JudgeVerdict.ACCEPTED.equals(status);
                outcomes.add(outcome(i, status, ok, result, testcase.getInputData(), testcase.getExpectedOutput()));
                time += result.cpuMs(); memory = Math.max(memory, result.memoryBytes());
                if (ok) passed++;
                else { if (firstFailure == null) firstFailure = status; if (!oi) { verdict = status; break; } }
            }
            java.time.LocalDateTime executionEnd = java.time.LocalDateTime.now();
            if (cases.isEmpty()) verdict = JudgeVerdict.ACCEPTED;
            else if (firstFailure != null) verdict = oi ? (passed > 0 ? JudgeVerdict.PARTIAL : firstFailure) : firstFailure;
            int total = cases.isEmpty() ? 1 : cases.size();
            int updated = updateContestResult(submission, problem, contest, verdict, time, memory, passed, total, json(outcomes));
            if (updated == 1) contestSubmissions.updateMetricsIfCurrentAttempt(submission.getId(), submission.getAttemptId(),
                    compileStart, compileEnd, executionStart, executionEnd, outputSize, lastExit, null, problem == null ? null : problem.getPackageDigest());
        } catch (IOException exception) {
            throw new IllegalStateException("无法准备沙箱工作目录", exception);
        } finally {
            deleteRecursively(work);
            queue.releaseExecutionSlot(slotSubject, slotToken);
        }
    }

    private int updateContestResult(ContestSubmission submission, ContestProblem problem, Contest contest, String verdict,
                                    long time, long memory, int passed, int total, String payload) {
        int updated = contestSubmissions.updateResultIfCurrentAttempt(submission.getId(), submission.getAttemptId(),
                verdict, (int) time, memory, passed, total, payload);
        if (updated == 1 && problem != null) {
            judgements.save(Judgement.of(submission, submission.getAttemptId(), verdict, payload, problem.getPackageDigest(), null));
            if (contest != null) { contest.requestScoreboardRefresh(); contests.save(contest); }
        }
        return updated;
    }

    /** custom-checker 作为占位表示使用题目 checker 配置（含 custom 时由 Checker 调 Judge0）。 */
    private String verdictOf(DockerSandboxClient.Result result, String mode, String checkerConfig,
                             String expected, String input) {
        if (result.memoryExceeded()) return JudgeVerdict.MEMORY_LIMIT_EXCEEDED;
        if (result.timedOut()) return JudgeVerdict.TIME_LIMIT_EXCEEDED;
        if (result.outputExceeded()) return JudgeVerdict.OUTPUT_LIMIT_EXCEEDED;
        if (result.returncode() == 0) {
            boolean ok = checkerConfig == null || "exact".equals(mode)
                    ? OutputChecker.matches("exact", result.stdout(), expected)
                    : safeCheck(checkerConfig, result.stdout(), expected, input);
            return ok ? JudgeVerdict.ACCEPTED : JudgeVerdict.WRONG_ANSWER;
        }
        int code = result.returncode();
        if (code == -11 || code == 139) return JudgeVerdict.SEGMENTATION_FAULT;
        return JudgeVerdict.RUNTIME_ERROR;
    }

    private boolean safeCheck(String checkerConfig, String actual, String expected, String input) {
        try { return checker.check(checkerConfig, actual, expected, input); }
        catch (Checker.CheckerUnavailableException exception) { return false; }
    }

    private Map<String, Object> outcome(int index, String status, boolean passed, DockerSandboxClient.Result result,
                                        String input, String expected) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("testCaseIndex", index); outcome.put("passed", passed); outcome.put("status", status);
        outcome.put("time_used", (int) result.cpuMs()); outcome.put("memory_used", (int) (result.memoryBytes() / 1024));
        outcome.put("stdout", result.stdout()); outcome.put("stderr", result.stderr());
        outcome.put("input", input); outcome.put("expected", expected);
        return outcome;
    }

    /** 返回编译错误信息；null 表示编译成功或无需编译。 */
    private String compile(Language language, String code, Path work) {
        writeSource(language, code, work);
        if (language.compile().isEmpty()) return null;
        DockerSandboxClient.Result result = sandbox.execute(language.compile(), "", 20, 1024, work, false, 2 * 1024 * 1024);
        if (result.returncode() != 0) {
            String error = result.stderr() == null || result.stderr().isBlank() ? result.stdout() : result.stderr();
            return error == null || error.isBlank() ? "Compilation Error" : error;
        }
        return null;
    }

    private void writeSource(Language language, String code, Path work) {
        try { Files.writeString(work.resolve(language.sourceName()), code, StandardCharsets.UTF_8); }
        catch (IOException exception) { throw new IllegalStateException("无法写入源代码", exception); }
    }

    private Language language(String raw, Path work) {
        String language = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        Path source;
        return switch (language) {
            case "cpp", "c++" -> { source = work.resolve("main.cpp");
                yield new Language("main.cpp", List.of("g++", "-O2", "-std=c++17", "-o", work.resolve("main").toString(), source.toString()), List.of(work.resolve("main").toString())); }
            case "c", "c11" -> { source = work.resolve("main.c");
                yield new Language("main.c", List.of("gcc", "-O2", "-std=c11", "-o", work.resolve("main").toString(), source.toString()), List.of(work.resolve("main").toString())); }
            case "python", "python3" -> { source = work.resolve("main.py");
                yield new Language("main.py", List.of(), List.of("python3", source.toString())); }
            case "java" -> { source = work.resolve("Main.java");
                yield new Language("Main.java", List.of("javac", "-d", work.toString(), source.toString()), List.of("java", "-cp", work.toString(), "Main")); }
            case "go" -> { source = work.resolve("main.go");
                yield new Language("main.go", List.of("go", "build", "-o", work.resolve("main").toString(), source.toString()), List.of(work.resolve("main").toString())); }
            case "javascript", "js" -> { source = work.resolve("main.js");
                yield new Language("main.js", List.of(), List.of("node", source.toString())); }
            default -> throw new IllegalArgumentException("不支持的编程语言: " + raw);
        };
    }

    private void deleteRecursively(Path path) {
        if (path == null) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "[]"; }
    }
}
