package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopsphere.userservice.TestProps;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.OperationNotAllowedException;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final long ADMIN_ID = 1L;
    private static final long TARGET_ID = 2L;

    @Mock private UserRepository users;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private AuditService audit;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, refreshTokens, audit, TestProps.CLOCK);
    }

    private User target(Role role, boolean enabled) {
        User user = new User();
        user.setId(TARGET_ID);
        user.setEmail("t@example.com");
        user.setRole(role);
        user.setEnabled(enabled);
        when(users.findById(TARGET_ID)).thenReturn(Optional.of(user));
        return user;
    }

    // ---------- enable / disable ----------

    @Test
    void admin_cannotDisableOwnAccount() {
        assertThrows(OperationNotAllowedException.class, () -> service.setEnabled(ADMIN_ID, ADMIN_ID, false));

        verify(users, never()).findById(anyLong());
    }

    @Test
    void disablingAUser_revokesTheirSessions_andIsAudited() {
        User user = target(Role.USER, true);

        service.setEnabled(ADMIN_ID, TARGET_ID, false);

        assertFalse(user.isEnabled());
        verify(refreshTokens).revokeAllForUser(TARGET_ID);
        verify(audit).record(eq(AuditAction.USER_DISABLED), eq(ADMIN_ID), eq(TARGET_ID), isNull());
    }

    @Test
    void enablingAUser_doesNotRevokeSessions() {
        User user = target(Role.USER, false);

        service.setEnabled(ADMIN_ID, TARGET_ID, true);

        assertTrue(user.isEnabled());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
        verify(audit).record(eq(AuditAction.USER_ENABLED), eq(ADMIN_ID), eq(TARGET_ID), isNull());
    }

    @Test
    void lastActiveAdmin_cannotBeDisabled() {
        User user = target(Role.ADMIN, true);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(OperationNotAllowedException.class, () -> service.setEnabled(ADMIN_ID, TARGET_ID, false));

        assertTrue(user.isEnabled());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
    }

    @Test
    void anAdmin_canBeDisabledWhenAnotherActiveAdminExists() {
        User user = target(Role.ADMIN, true);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);

        service.setEnabled(ADMIN_ID, TARGET_ID, false);

        assertFalse(user.isEnabled());
    }

    // ---------- roles ----------

    @Test
    void admin_cannotChangeOwnRole() {
        assertThrows(OperationNotAllowedException.class, () -> service.changeRole(ADMIN_ID, ADMIN_ID, Role.USER));
    }

    @Test
    void lastActiveAdmin_cannotBeDemoted() {
        User user = target(Role.ADMIN, true);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(OperationNotAllowedException.class, () -> service.changeRole(ADMIN_ID, TARGET_ID, Role.USER));

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void promotingAUser_changesRole_andRecordsOldAndNewRole() {
        User user = target(Role.USER, true);

        service.changeRole(ADMIN_ID, TARGET_ID, Role.ADMIN);

        assertEquals(Role.ADMIN, user.getRole());
        verify(audit).record(eq(AuditAction.ROLE_CHANGED), eq(ADMIN_ID), eq(TARGET_ID), eq("USER -> ADMIN"));
    }

    @Test
    void settingTheSameRole_isANoOp() {
        target(Role.USER, true);

        service.changeRole(ADMIN_ID, TARGET_ID, Role.USER);

        verify(audit, never()).record(eq(AuditAction.ROLE_CHANGED), anyLong(), anyLong(), eq("USER -> USER"));
    }

    // ---------- unlock ----------

    @Test
    void unlock_clearsLoginState_andIsAudited() {
        when(users.existsById(TARGET_ID)).thenReturn(true);

        service.unlock(ADMIN_ID, TARGET_ID);

        verify(users).resetLoginState(TARGET_ID);
        verify(audit).record(eq(AuditAction.ACCOUNT_UNLOCKED), eq(ADMIN_ID), eq(TARGET_ID), isNull());
    }

    @Test
    void unlock_unknownUser_isNotFound() {
        when(users.existsById(TARGET_ID)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> service.unlock(ADMIN_ID, TARGET_ID));

        verify(users, never()).resetLoginState(anyLong());
    }
}
