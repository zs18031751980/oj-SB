package com.xauat.oj.api.submission;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.api.judge.JudgeResultView;
import com.xauat.oj.api.problem.StaticProblemCatalog;
import com.xauat.oj.core.problem.repository.ProblemRepository;
import com.xauat.oj.core.submission.domain.Submission;
import com.xauat.oj.core.submission.repository.SubmissionOutboxRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.user.domain.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 提交详情越权测试（IDOR）：普通用户不能通过修改 submissionId 读取他人提交。
 */
class SubmissionControllerAccessTest {

    private SubmissionController controller(CurrentUser currentUser, SubmissionRepository submissions,
            SubmissionCache cache) {
        return new SubmissionController(currentUser, mock(ProblemRepository.class), submissions,
                mock(SubmissionOutboxRepository.class), mock(StaticProblemCatalog.class), mock(JudgeResultView.class),
                cache);
    }

    private CurrentUser currentUser(int userId, String role) {
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(role);
        when(currentUser.require("Bearer token")).thenReturn(user);
        return currentUser;
    }

    private SubmissionRepository repositoryOwnedBy(int ownerId, Submission submission) {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        when(submission.getUserId()).thenReturn(ownerId);
        when(submissions.findById(9)).thenReturn(Optional.of(submission));
        return submissions;
    }

    private SubmissionCache emptyCache() {
        SubmissionCache cache = mock(SubmissionCache.class);
        when(cache.get("cache:submission:9")).thenReturn(Optional.empty());
        return cache;
    }

    @Test
    void otherMemberCannotReadSubmission() {
        var response = controller(currentUser(2, "member"), repositoryOwnedBy(1, mock(Submission.class)), emptyCache())
                .detail("Bearer token", 9);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void ownerCanReadOwnSubmission() {
        var response = controller(currentUser(1, "member"), repositoryOwnedBy(1, mock(Submission.class)), emptyCache())
                .detail("Bearer token", 9);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void managerCanReadAnySubmission() {
        var response = controller(currentUser(99, "manager"), repositoryOwnedBy(1, mock(Submission.class)), emptyCache())
                .detail("Bearer token", 9);

        assertEquals(200, response.getStatusCode().value());
    }
}
