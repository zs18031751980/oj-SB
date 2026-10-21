package com.xauat.oj.api.admin;

import com.xauat.oj.api.auth.CurrentUser;
import com.xauat.oj.core.announcement.repository.AnnouncementRepository;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.submission.repository.SubmissionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * 账号生命周期测试：停用账号必须撤销全部会话，启用不恢复旧会话。
 */
class AdminUserControllerSessionTest {

    private AdminUserController controller(User target, AuthSessionRepository sessions) {
        CurrentUser currentUser = mock(CurrentUser.class);
        User manager = mock(User.class);
        when(manager.getRole()).thenReturn("manager");
        when(currentUser.require(any())).thenReturn(manager);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(7)).thenReturn(Optional.of(target));
        return new AdminUserController(currentUser, users, mock(SubmissionRepository.class),
                mock(AnnouncementRepository.class), sessions);
    }

    @Test
    void disablingUserRevokesSessions() {
        User target = mock(User.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        controller(target, sessions).status("Bearer x", 7, new AdminUserController.StatusRequest(false));
        verify(target).setActive(false);
        verify(sessions).revokeByUserId(7);
    }

    @Test
    void enablingUserDoesNotRestoreSessions() {
        User target = mock(User.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        controller(target, sessions).status("Bearer x", 7, new AdminUserController.StatusRequest(true));
        verify(target).setActive(true);
        verify(sessions, never()).revokeByUserId(anyInt());
    }
}
