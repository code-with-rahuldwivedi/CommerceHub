package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.dto.ChangePasswordRequest;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.TokenType;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.entity.UserToken;
import com.shopsphere.userservice.exception.IncorrectPasswordException;
import com.shopsphere.userservice.exception.InvalidRequestException;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.util.AfterCommit;
import com.shopsphere.userservice.util.Emails;

/** Credential-related flows: change/reset password, e-mail verification, session revocation. */
@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UserTokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final AuditService audit;
    private final MailService mail;
    private final AuthMetrics metrics;
    private final AppProperties.Account props;
    private final Clock clock;

    public AccountService(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy,
                          UserTokenService tokens,
                          RefreshTokenService refreshTokens,
                          LoginAttemptService loginAttempts,
                          AuditService audit,
                          MailService mail,
                          AuthMetrics metrics,
                          AppProperties appProperties,
                          Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.mail = mail;
        this.metrics = metrics;
        this.props = appProperties.account();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ e-mail verification

    /** Issues a verification token and mails it after the surrounding transaction commits. */
    @Transactional
    public void sendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        String raw = tokens.issue(user.getId(), TokenType.EMAIL_VERIFICATION, props.verificationTtl());
        String email = user.getEmail();
        String name = user.getName();
        AfterCommit.run(() -> mail.sendVerificationEmail(email, name, raw));
    }

    /** Always completes silently: the caller must not learn whether the address exists. */
    @Transactional
    public void resendVerificationEmail(String rawEmail) {
        users.findByEmail(Emails.normalize(rawEmail)).ifPresent(user -> {
            if (!user.isEmailVerified() && user.isEnabled()
                    && !tokens.recentlyIssued(user.getId(), TokenType.EMAIL_VERIFICATION, props.resendCooldown())) {
                sendVerificationEmail(user);
            }
        });
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        long userId = tokens.consume(rawToken, TokenType.EMAIL_VERIFICATION);
        User user = users.findById(userId).orElseThrow(InvalidTokenException::new);
        user.setEmailVerified(true);
        metrics.account("email_verified");
        audit.record(AuditAction.EMAIL_VERIFIED, userId, userId, null);
    }

    // ------------------------------------------------------------------ password reset

    /** Always completes silently (same response for unknown e-mails); cooldown prevents mail bombing. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        users.findByEmail(Emails.normalize(rawEmail)).ifPresent(user -> {
            if (!user.isEnabled()
                    || tokens.recentlyIssued(user.getId(), TokenType.PASSWORD_RESET, props.resendCooldown())) {
                return;
            }
            String raw = tokens.issue(user.getId(), TokenType.PASSWORD_RESET, props.passwordResetTtl());
            String email = user.getEmail();
            audit.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), user.getId(), null);
            AfterCommit.run(() -> mail.sendPasswordResetEmail(email, raw));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UserToken token = tokens.find(rawToken, TokenType.PASSWORD_RESET);
        User user = users.findById(token.getUserId()).orElseThrow(InvalidTokenException::new);

        // Validate BEFORE consuming, so a too-weak password does not burn the user's reset link.
        passwordPolicy.validate(newPassword, user.getEmail());
        tokens.markUsed(token);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now(clock));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setEmailVerified(true); // reading the e-mail proved ownership
        refreshTokens.revokeAllForUser(user.getId());

        metrics.account("password_reset");
        audit.record(AuditAction.PASSWORD_RESET_COMPLETED, user.getId(), user.getId(), null);
        String email = user.getEmail();
        AfterCommit.run(() -> mail.sendPasswordChangedEmail(email));
    }

    // ------------------------------------------------------------------ authenticated flows

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            // Counts toward lockout too, so a stolen access token cannot be used to guess the password.
            loginAttempts.recordFailure(user.getEmail());
            throw new IncorrectPasswordException();
        }
        passwordPolicy.validate(request.newPassword(), user.getEmail());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidRequestException("New password must differ from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now(clock));
        refreshTokens.revokeAllForUser(userId); // every session must sign in again

        metrics.account("password_changed");
        audit.record(AuditAction.PASSWORD_CHANGED, userId, userId, null);
        String email = user.getEmail();
        AfterCommit.run(() -> mail.sendPasswordChangedEmail(email));
    }

    /** "Sign out everywhere". Access tokens already issued stay valid until they expire (short TTL). */
    @Transactional
    public void revokeAllSessions(long userId) {
        refreshTokens.revokeAllForUser(userId);
        metrics.account("sessions_revoked");
        audit.record(AuditAction.SESSIONS_REVOKED, userId, userId, null);
    }
}
