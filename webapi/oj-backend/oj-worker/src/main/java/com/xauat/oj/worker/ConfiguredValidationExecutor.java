package com.xauat.oj.worker;

import com.xauat.oj.core.contest.domain.ContestProblem;
import com.xauat.oj.core.contest.domain.ReferenceValidationJob;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestTestcaseRepository;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.infrastructure.judge.Checker;
import com.xauat.oj.infrastructure.judge.ExecutionClient;
import com.xauat.oj.infrastructure.judge.Judge0Client;
import com.xauat.oj.infrastructure.judge.JudgeVerdict;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费 testcase_gen_queue：参考答案验证（题目）与题包验证（package:&lt;digest&gt;）。 */
@Component
@ConditionalOnProperty(name = "oj.judge.enabled", havingValue = "true")
public class ConfiguredValidationExecutor implements ValidationExecutor {
    private static final String PACKAGE_PREFIX = "package:";

    private final ReferenceValidationJobRepository jobs;
    private final ContestProblemRepository problems;
    private final ContestTestcaseRepository testcases;
    private final PackageValidator packageValidator;
    private final ExecutionClient judge0;
    private final Checker checker;

    public ConfiguredValidationExecutor(ReferenceValidationJobRepository jobs, ContestProblemRepository problems,
                                        ContestTestcaseRepository testcases, PackageValidator packageValidator,
                                        ExecutionClient judge0, Checker checker) {
        this.jobs = jobs; this.problems = problems; this.testcases = testcases;
        this.packageValidator = packageValidator; this.judge0 = judge0; this.checker = checker;
    }

    @Override
    public void execute(String jobId) {
        if (jobId != null && jobId.startsWith(PACKAGE_PREFIX)) {
            packageValidator.validate(jobId.substring(PACKAGE_PREFIX.length()));
            return;
        }
        validateReference(jobId);
    }

    public void validateReference(String jobId) {
        ReferenceValidationJob job = jobs.findById(jobId).orElse(null);
        if (job == null || "DONE".equals(job.getState())) return;
        ContestProblem problem = problems.findById(job.getProblemId()).orElse(null);
        if (problem == null) { job.markDone(); jobs.save(job); return; }
        // 版本栅栏：旧校验不得覆盖新题目。
        if (job.getVersion() != problem.getValidationVersion()) { job.markDone(); jobs.save(job); return; }
        String checkerConfig = problem.getCheckerConfig();
        try {
            var cases = testcases.findByContestProblem_IdOrderBySortOrderAscIdAsc(problem.getId());
            if (cases.isEmpty()) throw new IllegalArgumentException("没有可用测试数据");
            for (var testcase : cases) {
                Judge0Client.Result result = judge0.run(problem.getCorrectAnswer(), problem.getLanguage(), testcase.getInputData(), 60);
                if (!JudgeVerdict.ACCEPTED.equals(JudgeVerdict.fromStatus(result.statusId(), result.description()))
                        || !checker.check(checkerConfig, result.stdout(), testcase.getExpectedOutput(), testcase.getInputData())) {
                    throw new IllegalArgumentException("参考答案未通过测试点 " + testcase.getSortOrder());
                }
            }
            problem.markValidated();
            job.markDone();
            problems.save(problem);
            jobs.save(job);
        } catch (IllegalArgumentException | Checker.CheckerUnavailableException exception) {
            problem.markValidationFailed(exception.getMessage());
            job.markDone();
            problems.save(problem);
            jobs.save(job);
        }
        // 其它基础设施异常向上抛出，保留任务供恢复，不计为 INVALID。
    }
}
