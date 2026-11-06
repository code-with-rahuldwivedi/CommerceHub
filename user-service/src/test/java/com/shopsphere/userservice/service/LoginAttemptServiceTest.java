package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopsphere.userservice.TestProps;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private UserRepository users;
    @Mock private AuditService audit;
    @Mock private MailService mail;
    @Mock private AuthMetrics metrics;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(users, TestProps.app(false), TestProps.CLOCK, audit, mail, metrics);
    }

    @Test
    void unknownEmail_isIgnored() {
        when(users.incrementFailedAttempts("a@b.com")).thenReturn(0);

        service.recordFailure("a@b.com");

        verify(users, never()).lockIfThresholdReached(anyString(), any(), anyInt());
        verifyNoInteractions(audit, mail, metrics);
    }

    @Test
    void belowThreshold_countsButDoesNotLock() {
        when(users.incrementFailedAttempts("a@b.com")).thenReturn(1);
        when(users.lockIfThresholdReached(eq("a@b.com"), any(), eq(5))).thenReturn(0);

        service.recordFailure("a@b.com");

        verifyNoInteractions(audit, mail, metrics);
    }

    @Test
    void reachingThreshold_locksForTheConfiguredDuration_audits_notifiesAndCounts() {
        when(users.incrementFailedAttempts("a@b.com")).thenReturn(1);
        when(users.lockIfThresholdReached(eq("a@b.com"), any(), eq(5))).thenReturn(1);

        service.recordFailure("a@b.com");

        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(users).lockIfThresholdReached(eq("a@b.com"), until.capture(), eq(5));
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 15), until.getValue()); // fixed clock + 15 minutes

        verify(metrics).lockout();
        verify(audit).record(eq(AuditAction.ACCOUNT_LOCKED), isNull(), isNull(), anyString());
        verify(mail).sendAccountLockedEmail(eq("a@b.com"), eq(until.getValue()));
    }

    @Test
    void reset_clearsLoginState() {
        service.reset(7L);

        verify(users).resetLoginState(7L);
    }
}
