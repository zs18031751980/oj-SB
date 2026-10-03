package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.core.contest.domain.Contest;
import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.domain.Judgement;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.contest.repository.ContestSubmissionRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.JudgementRepository;
import com.xauat.oj.core.problem.repository.TestcaseRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.infrastructure.judge.Checker;
import com.xauat.oj.infrastructure.judge.ExecutionClient;
import com.xauat.oj.infrastructure.judge.Judge0Client;
import com.xauat.oj.infrastructure.judge.JudgeVerdict;
import com.xauat.oj.infrastructure.judge.OutputChecker;
import com.xauat.oj.infrastructure.queue.JudgeQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产环境通过 oj.judge.enabled=true 启用 Judge0 适配器；未启用时由 DisabledJudgeExecutor 保留任务。
 */
@Component
@ConditionalOnExpression("('${oj.judge.executor:judge0}' == 'judge0' or '${oj.judge.executor:judge0}' == 'local') and ${oj.judge.enabled:false}")
public class ConfiguredJudgeExecutor implements JudgeExecutor {
    private final SubmissionRepository submissions;
    private final ContestSubmissionRepository contestSubmissions;
    private final TestcaseRepository testcases;
    private final JudgementRepository judgements;
    private final ContestTestcaseRepository contestTestcases;
    private final ContestProblemRepository contestProblems;
    private final ContestRepository contests;
    private final ExecutionClient judge0;
    private final Checker checker;
    private final JudgeQueue queue;
    private final JudgeStageRecorder stageRecorder;
    private final ObjectMapper mapper;

    public ConfiguredJudgeExecutor(SubmissionRepository submissions, ContestSubmissionRepository contestSubmissions,
                                   TestcaseRepository testcases, JudgementRepository judgements,
                                   ContestTestcaseRepository contestTestcases, ContestProblemRepository contestProblems,
                                   ContestRepository contests, ExecutionClient judge0, Checker checker, JudgeQueue queue,
                                   JudgeStageRecorder stageRecorder, ObjectMapper mapper) {
        this.submissions = submissions; this.contestSubmissions = contestSubmissions; this.testcases = testcases;
        this.judgements = judgements; this.contestTestcases = contestTestcases; this.contestProblems = contestProblems;
        this.contests = contests; this.judge0 = judge0; this.checker = checker; this.queue = queue;
        this.stageRecorder = stageRecorder; this.mapper = mapper;
    }

    @Override
    public void execute(String jobId) {
        var regular = submissions.findByJobId(jobId);
        if (regular.isPresent()) { judgeRegular(regular.get()); return; }
        var contest = contestSubmissions.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("未知判题任务: " + jobId));
        judgeContest(contest);
    }

    private void judgeRegular(com.xauat.oj.core.submission.domain.Submission submission) {
        submission.markRunning();
        submissions.save(submission);
        String slotSubject = "user:" + submission.getUserId();
        String slotToken = "job:" + submission.getJobId();
        if (!queue.acquireExecutionSlot(slotSubject, slotToken, 3, 60)) {
            throw new IllegalStateException("执行并发槽已满: " + slotSubject);
        }
        var cases = testcases.findByProblemIdOrderBySortOrderAscIdAsc(submission.getProblemId());
        List<Map<String, Object>> outcomes = new ArrayList<>();
        int passed = 0; int totalTime = 0; int maxMemory = 0; String verdict = JudgeVerdict.ACCEPTED; Integer failIndex = null;
        for (int i = 0; i < cases.size(); i++) {
            var testcase = cases.get(i);
            Judge0Client.Result result = judge0.run(submission.getCode(), submission.getLanguage(), testcase.getInputData(), 60);
            String status = JudgeVerdict.fromStatus(result.statusId(), result.description());
            boolean ok = false;
            if (JudgeVerdict.ACCEPTED.equals(status)) {
                ok = OutputChecker.matches("exact", result.stdout(), testcase.getOutputData());
                if (!ok) status = JudgeVerdict.WRONG_ANSWER;
            }
            outcomes.add(caseOutcome(i, status, ok, result, testcase.getInputData(), testcase.getOutputData()));
            totalTime += result.timeMs() == null ? 0 : result.timeMs();
            maxMemory = Math.max(maxMemory, result.memoryKb() == null ? 0 : result.memoryKb());
            if (ok) passed++;
            else { failIndex = i; verdict = status; break; }
        }
        if (cases.isEmpty()) verdict = JudgeVerdict.ACCEPTED;
        submission.recordResult(verdict, totalTime, maxMemory, json(outcomes), failIndex);
        submissions.save(submission);
        if (submission.getCreatedAt() != null) {
            stageRecorder.observe("all", "total", java.time.Duration.between(submission.getCreatedAt(), java.time.LocalDateTime.now()).toMillis() / 1000.0);
        }
        queue.releaseExecutionSlot(slotSubject, slotToken);
    }

    private void judgeContest(com.xauat.oj.core.contest.domain.ContestSubmission submission) {
        submission.markRunning("judge0-worker");
        contestSubmissions.save(submission);
        var cases = contestTestcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(submission.getContestProblemId());
        ContestProblem problem = contestProblems.findById(submission.getContestProblemId()).orElse(null);
        String checkerConfig = problem == null ? "{}" : problem.getCheckerConfig();
        Contest contest = problem == null ? null : contests.findById(problem.getContestId()).orElse(null);
        boolean oi = contest != null && "OI".equalsIgnoreCase(contest.getContestType());
        String slotSubject = "user:" + submission.getUserId();
        String slotToken = "job:" + submission.getJobId();
        if (!queue.acquireExecutionSlot(slotSubject, slotToken, contest != null ? Math.max(1, contest.getActiveSubmissionLimit()) : 3, 60)) {
            throw new IllegalStateException("执行并发槽已满: " + slotSubject);
        }
        java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
        List<Map<String, Object>> outcomes = new ArrayList<>();
        int passed = 0; Integer totalTime = 0; Integer maxMemory = 0; String verdict = JudgeVerdict.ACCEPTED; String firstFailure = null;
        int outputSize = 0; Integer lastExitCode = null;
        for (int i = 0; i < cases.size(); i++) {
            var testcase = cases.get(i);
            Judge0Client.Result result = judge0.run(submission.getCode(), submission.getLanguage(), testcase.getInputData(), 60);
            outputSize += result.stdout() == null ? 0 : result.stdout().length();
            lastExitCode = result.exitCode();
            String status = JudgeVerdict.fromStatus(result.statusId(), result.description());
            boolean ok = false;
            if (JudgeVerdict.ACCEPTED.equals(status)) {
                try { ok = checker.check(checkerConfig, result.stdout(), testcase.getExpectedOutput(), testcase.getInputData()); }
                catch (Checker.CheckerUnavailableException exception) { status = JudgeVerdict.SYSTEM_ERROR; }
                if (JudgeVerdict.ACCEPTED.equals(status) && !ok) status = JudgeVerdict.WRONG_ANSWER;
            }
            outcomes.add(caseOutcome(i, status, ok, result, testcase.getInputData(), testcase.getExpectedOutput()));
            totalTime += result.timeMs() == null ? 0 : result.timeMs();
            maxMemory = Math.max(maxMemory, result.memoryKb() == null ? 0 : result.memoryKb());
            if (ok) passed++;
            else {
                if (firstFailure == null) firstFailure = status;
                if (!oi) { verdict = status; break; }
            }
        }
        if (cases.isEmpty()) verdict = JudgeVerdict.ACCEPTED;
        else if (!JudgeVerdict.ACCEPTED.equals(firstFailure)) verdict = oi ? (passed > 0 ? JudgeVerdict.PARTIAL : firstFailure) : firstFailure;
        int total = cases.isEmpty() ? 1 : cases.size();
        int updated = contestSubmissions.updateResultIfCurrentAttempt(submission.getId(), submission.getAttemptId(),
                verdict, totalTime, maxMemory == null ? null : maxMemory.longValue(), passed, total, json(outcomes));
        if (updated == 1) {
            java.time.LocalDateTime finishedAt = java.time.LocalDateTime.now();
            contestSubmissions.updateMetricsIfCurrentAttempt(submission.getId(), submission.getAttemptId(), startedAt, finishedAt,
                    startedAt, finishedAt, outputSize, lastExitCode, null, problem == null ? null : problem.getPackageDigest());
            if (problem != null) judgements.save(Judgement.of(submission, submission.getAttemptId(), verdict, json(outcomes), problem.getPackageDigest(), null));
            if (contest != null) { contest.requestScoreboardRefresh(); contests.save(contest); }
        }
        if (submission.getReceivedAt() != null) {
            stageRecorder.observe("all", "total", java.time.Duration.between(submission.getReceivedAt(), java.time.LocalDateTime.now()).toMillis() / 1000.0);
        }
        queue.releaseExecutionSlot(slotSubject, slotToken);
    }

    private Map<String, Object> caseOutcome(int index, String status, boolean passed, Judge0Client.Result result, String input, String expected) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("testCaseIndex", index);
        outcome.put("passed", passed);
        outcome.put("status", status);
        outcome.put("time_used", result.timeMs());
        outcome.put("memory_used", result.memoryKb());
        outcome.put("stdout", result.stdout());
        outcome.put("stderr", JudgeVerdict.COMPILATION_ERROR.equals(status) && !result.compileOutput().isBlank() ? result.compileOutput() : result.stderr());
        outcome.put("input", input);
        outcome.put("expected", expected);
        return outcome;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "[]"; }
    }
}
