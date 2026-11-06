package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.shopsphere.userservice.exception.UserNotFoundException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopsphere.userservice.TestProps;
import com.shopsphere.userservice.dto.ChangePasswordRequest;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.TokenType;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.entity.UserToken;
import com.shopsphere.userservice.exception.IncorrectPasswordException;
import com.shopsphere.userservice.exception.InvalidRequestException;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String NEW_PASSWORD = "NewPassw0rd!x";

    @Mock private UserRepository users;
    @Mock private PasswordEncoder encoder;
    @Mock private PasswordPolicy policy;
    @Mock private UserTokenService tokens;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private LoginAttemptService loginAttempts;
    @Mock private AuditService audit;
    @Mock private MailService mail;
    @Mock private AuthMetrics metrics;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(users, encoder, policy, tokens, refreshTokens, loginAttempts,
                audit, mail, metrics, TestProps.app(false), TestProps.CLOCK);
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("a@b.com");
        user.setName("Asha");
        user.setPassword("OLDHASH");
        return user;
    }

    // ---------- change password ----------

    @Test
    void changePassword_wrongCurrentPassword_countsFailure_andChangesNothing() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(encoder.matches("wrong", "OLDHASH")).thenReturn(false);

        assertThrows(IncorrectPasswordException.class,
                () -> service.changePassword(1L, new ChangePasswordRequest("wrong", NEW_PASSWORD)));

        verify(loginAttempts).recordFailure("a@b.com");
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
        verifyNoInteractions(mail);
    }

    @Test
    void changePassword_success_updatesHash_revokesAllSessions_audits_andNotifies() {
        User user = user(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(encoder.matches("old", "OLDHASH")).thenReturn(true);
        when(encoder.matches(NEW_PASSWORD, "OLDHASH")).thenReturn(false);
        when(encoder.encode(NEW_PASSWORD)).thenReturn("NEWHASH");

        service.changePassword(1L, new ChangePasswordRequest("old", NEW_PASSWORD));

        assertEquals("NEWHASH", user.getPassword());
        assertEquals(LocalDateTime.now(TestProps.CLOCK), user.getPasswordChangedAt());
        verify(policy).validate(NEW_PASSWORD, "a@b.com");
        verify(refreshTokens).revokeAllForUser(1L);
        verify(audit).record(eq(AuditAction.PASSWORD_CHANGED), eq(1L), eq(1L), isNull());
        verify(mail).sendPasswordChangedEmail("a@b.com");
    }

    @Test
    void changePassword_sameAsCurrent_isRejected() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(encoder.matches("old", "OLDHASH")).thenReturn(true);
        when(encoder.matches(NEW_PASSWORD, "OLDHASH")).thenReturn(true);

        assertThrows(InvalidRequestException.class,
                () -> service.changePassword(1L, new ChangePasswordRequest("old", NEW_PASSWORD)));

        verify(encoder, never()).encode(anyString());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
    }

    // ---------- reset password ----------

    @Test
    void resetPassword_validatesPolicyBeforeConsumingTheToken_thenAppliesAndClearsLockout() {
        UserToken token = mock(UserToken.class);
        when(token.getUserId()).thenReturn(1L);
        when(tokens.find("raw", TokenType.PASSWORD_RESET)).thenReturn(token);
        User user = user(1L);
        user.setFailedAttempts(3);
        user.setLockedUntil(LocalDateTime.now(TestProps.CLOCK).plusMinutes(10));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(encoder.encode(NEW_PASSWORD)).thenReturn("NEWHASH");

        service.resetPassword("raw", NEW_PASSWORD);

        InOrder order = inOrder(policy, tokens);
        order.verify(policy).validate(NEW_PASSWORD, "a@b.com");
        order.verify(tokens).markUsed(token);

        assertEquals("NEWHASH", user.getPassword());
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockedUntil());
        assertTrue(user.isEmailVerified());
        verify(refreshTokens).revokeAllForUser(1L);
        verify(audit).record(eq(AuditAction.PASSWORD_RESET_COMPLETED), eq(1L), eq(1L), isNull());
        verify(mail).sendPasswordChangedEmail("a@b.com");
    }

    @Test
    void resetPassword_weakPassword_doesNotBurnTheToken() {
        UserToken token = mock(UserToken.class);
        when(token.getUserId()).thenReturn(1L);
        when(tokens.find("raw", TokenType.PASSWORD_RESET)).thenReturn(token);
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        doThrow(new InvalidRequestException("too common")).when(policy).validate(anyString(), anyString());

        assertThrows(InvalidRequestException.class, () -> service.resetPassword("raw", "password123"));

        verify(tokens, never()).markUsed(any());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
    }

    @Test
    void resetPassword_invalidToken_isRejected() {
        when(tokens.find("bad", TokenType.PASSWORD_RESET)).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> service.resetPassword("bad", NEW_PASSWORD));

        verifyNoInteractions(encoder, refreshTokens, mail);
    }

    // ---------- request reset ----------

    @Test
    void requestPasswordReset_unknownEmail_doesNothing() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("  Ghost@Example.com ");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void requestPasswordReset_withinCooldown_sendsNothing() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user(1L)));
        when(tokens.recentlyIssued(1L, TokenType.PASSWORD_RESET, Duration.ofMinutes(1))).thenReturn(true);

        service.requestPasswordReset("a@b.com");

        verify(tokens, never()).issue(anyLong(), any(), any());
        verifyNoInteractions(mail);
    }

    @Test
    void requestPasswordReset_disabledUser_sendsNothing() {
        User disabled = user(1L);
        disabled.setEnabled(false);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(disabled));

        service.requestPasswordReset("a@b.com");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void requestPasswordReset_issuesTokenAndMailsIt() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user(1L)));
        when(tokens.recentlyIssued(1L, TokenType.PASSWORD_RESET, Duration.ofMinutes(1))).thenReturn(false);
        when(tokens.issue(eq(1L), eq(TokenType.PASSWORD_RESET), any(Duration.class))).thenReturn("rawTok");

        service.requestPasswordReset("A@b.com");

        verify(mail).sendPasswordResetEmail("a@b.com", "rawTok");
        verify(audit).record(eq(AuditAction.PASSWORD_RESET_REQUESTED), eq(1L), eq(1L), isNull());
    }

    // ---------- e-mail verification ----------

    @Test
    void verifyEmail_marksUserVerified_andAudits() {
        User user = user(1L);
        when(tokens.consume("raw", TokenType.EMAIL_VERIFICATION)).thenReturn(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));

        service.verifyEmail("raw");

        assertTrue(user.isEmailVerified());
        verify(audit).record(eq(AuditAction.EMAIL_VERIFIED), eq(1L), eq(1L), isNull());
    }

    @Test
    void sendVerificationEmail_skipsAlreadyVerifiedUsers() {
        User user = user(1L);
        user.setEmailVerified(true);

        service.sendVerificationEmail(user);

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void sendVerificationEmail_issuesTokenAndMailsIt() {
        when(tokens.issue(eq(1L), eq(TokenType.EMAIL_VERIFICATION), any(Duration.class))).thenReturn("verTok");

        service.sendVerificationEmail(user(1L));

        verify(mail).sendVerificationEmail("a@b.com", "Asha", "verTok");
    }

    @Test
    void resendVerification_unknownOrVerifiedOrCoolingDown_sendsNothing() {
        User verified = user(1L);
        verified.setEmailVerified(true);
        when(users.findByEmail("verified@example.com")).thenReturn(Optional.of(verified));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.resendVerificationEmail("verified@example.com");
        service.resendVerificationEmail("ghost@example.com");

        verifyNoInteractions(tokens, mail);
    }

    // ---------- sessions ----------

    @Test
    void revokeAllSessions_revokes_audits_andCounts() {
        service.revokeAllSessions(1L);

        verify(refreshTokens).revokeAllForUser(1L);
        verify(metrics).account("sessions_revoked");
        verify(audit).record(eq(AuditAction.SESSIONS_REVOKED), eq(1L), eq(1L), isNull());
    }
    
    @Test
    void changePassword_userNotFound_throwsUserNotFoundException() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.changePassword(
                        99L,
                        new ChangePasswordRequest("old", NEW_PASSWORD)));

        verifyNoInteractions(encoder, refreshTokens, mail);
    }

    @Test
    void verifyEmail_userNotFound_throwsInvalidTokenException() {
        when(tokens.consume("raw", TokenType.EMAIL_VERIFICATION))
                .thenReturn(99L);
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> service.verifyEmail("raw"));

        verify(metrics, never()).account(anyString());
        verify(audit, never()).record(any(), anyLong(), anyLong(), any());
    }

    @Test
    void resetPassword_userNotFound_throwsInvalidTokenException() {
        UserToken token = mock(UserToken.class);
        when(token.getUserId()).thenReturn(99L);
        when(tokens.find("raw", TokenType.PASSWORD_RESET))
                .thenReturn(token);
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> service.resetPassword("raw", NEW_PASSWORD));

        verifyNoInteractions(encoder, refreshTokens, mail);
    }

    @Test
    void resendVerification_disabledUser_doesNotIssueToken() {
        User disabled = user(1L);
        disabled.setEnabled(false);

        when(users.findByEmail("a@b.com"))
                .thenReturn(Optional.of(disabled));

        service.resendVerificationEmail("a@b.com");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void resendVerification_cooldownActive_doesNotIssueToken() {
        when(users.findByEmail("a@b.com"))
                .thenReturn(Optional.of(user(1L)));

        when(tokens.recentlyIssued(
                1L,
                TokenType.EMAIL_VERIFICATION,
                Duration.ofMinutes(1)))
                .thenReturn(true);

        service.resendVerificationEmail("a@b.com");

        verify(tokens, never()).issue(anyLong(), any(), any());
        verifyNoInteractions(mail);
    }

    @Test
    void resendVerification_success_issuesTokenAndSendsEmail() {
        when(users.findByEmail("a@b.com"))
                .thenReturn(Optional.of(user(1L)));

        when(tokens.recentlyIssued(
                1L,
                TokenType.EMAIL_VERIFICATION,
                Duration.ofMinutes(1)))
                .thenReturn(false);

        when(tokens.issue(
                eq(1L),
                eq(TokenType.EMAIL_VERIFICATION),
                any(Duration.class)))
                .thenReturn("verification-token");

        service.resendVerificationEmail("a@b.com");

        verify(mail).sendVerificationEmail(
                "a@b.com",
                "Asha",
                "verification-token");
    }
}
