package com.xauat.oj.api.integration;

import com.xauat.oj.core.announcement.repository.AnnouncementRepository;
import com.xauat.oj.core.contest.repository.ContestProblemRepository;
import com.xauat.oj.core.contest.repository.ContestRepository;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.auth.repository.JuryMFAStateRepository;
import com.xauat.oj.core.contest.repository.ContestPackageRepository;
import com.xauat.oj.core.contest.repository.ReferenceValidationJobRepository;
import com.xauat.oj.core.ranking.repository.UserJudgeStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用真实 PostgreSQL 跑 Flyway 迁移并校验 JPA 映射/派生查询能正常装配。
 * 无 Docker 环境自动跳过（disabledWithoutDocker）。
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RepositorySchemaTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired AuthSessionRepository authSessions;
    @Autowired JuryMFAStateRepository juryMfa;
    @Autowired ProblemRepository problems;
    @Autowired SubmissionRepository submissions;
    @Autowired ContestRepository contests;
    @Autowired ContestProblemRepository contestProblems;
    @Autowired ContestPackageRepository contestPackages;
    @Autowired ReferenceValidationJobRepository validationJobs;
    @Autowired UserJudgeStatsRepository judgeStats;
    @Autowired AnnouncementRepository announcements;

    @Test
    void migrationsAndRepositoriesWire() {
        assertNotNull(users);
        assertNotNull(authSessions);
        assertNotNull(juryMfa);
        assertNotNull(problems);
        assertNotNull(submissions);
        assertNotNull(contests);
        assertNotNull(contestProblems);
        assertNotNull(contestPackages);
        // 触发派生查询解析，验证实体属性名与仓库方法一致。
        submissions.findByUser_IdOrderByIdDesc(1);
        validationJobs.findByProblem_IdOrderByCreatedAtDesc(1);
        validationJobs.findTop20ByStateOrderByCreatedAtAsc("PENDING");
        contestPackages.findTop50ByValidationStateInOrderByUpdatedAtAsc(java.util.List.of("PENDING"));
        contestProblems.findByContest_IdOrderBySortOrderAscIdAsc(1);
        judgeStats.findAll();
        announcements.findAll();
    }
}
