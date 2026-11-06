package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.util.AfterCommit;
import com.shopsphere.userservice.util.LogSanitizer;

/**
 * Brute-force protection. Every method runs in its OWN transaction (REQUIRES_NEW): the callers
 * (login, change-password) end in an exception, which must not roll the counter back.
 */
@Service
public class LoginAttemptService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final UserRepository users;
    private final AppProperties.Lockout props;
    private final Clock clock;
    private final AuditService audit;
    private final MailService mail;
    private final AuthMetrics metrics;

    public LoginAttemptService(UserRepository users, AppProperties appProperties, Clock clock,
                               AuditService audit, MailService mail, AuthMetrics metrics) {
        this.users = users;
        this.props = appProperties.lockout();
        this.clock = clock;
        this.audit = audit;
        this.mail = mail;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {

        int updated = users.incrementFailedAttempts(email);
        
        if (updated == 0) {
            return;
        }

        LocalDateTime until = LocalDateTime.now(clock).plus(props.lockDuration());

        int locked = users.lockIfThresholdReached(
                email, until, props.maxAttempts());       

        if (locked > 0) {
            metrics.lockout();

            audit.record(
                    AuditAction.ACCOUNT_LOCKED,
                    null,
                    null,
                    "email=" + LogSanitizer.maskEmail(email) + " until=" + until
            );

            AUDIT.warn("account_locked email={} until={}",
                    LogSanitizer.maskEmail(email), until);

            AfterCommit.run(() -> mail.sendAccountLockedEmail(email, until));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reset(long userId) {
        users.resetLoginState(userId);
    }
}
