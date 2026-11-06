package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.shopsphere.userservice.config.AppProperties;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> senderProvider;

    @Mock
    private JavaMailSender sender;

    private MailService service;

    private AppProperties properties(boolean logOnly) {
        return new AppProperties(
                new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppProperties.RateLimit(true, 100),
                new AppProperties.Bootstrap("admin@test.com", "password", "Admin"),
                new AppProperties.Account(
                        true,
                        Duration.ofHours(24),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(5),
                        "http://localhost:3000/verify?token={token}",
                        "http://localhost:3000/reset?token={token}"
                ),
                new AppProperties.Mail("noreply@shopsphere.com", logOnly),
                new AppProperties.Audit(90),
                new AppProperties.Request(1048576)
        );
    }

    @BeforeEach
    void setUp() {
        service = new MailService(senderProvider, properties(false));
    }

    @Test
    void warnIfNotConfigured_whenLogOnlyEnabled() {
        service = new MailService(senderProvider, properties(true));

        service.warnIfNotConfigured();

        verifyNoInteractions(senderProvider);
    }

    @Test
    void warnIfNotConfigured_whenSmtpUnavailable() {
        when(senderProvider.getIfAvailable()).thenReturn(null);

        service.warnIfNotConfigured();

        verify(senderProvider).getIfAvailable();
    }

    @Test
    void warnIfNotConfigured_whenSmtpAvailable() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        service.warnIfNotConfigured();

        verify(senderProvider).getIfAvailable();
    }

    @Test
    void sendVerificationEmail_sendsEncodedTokenAndCorrectDetails() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        service.sendVerificationEmail(
                "user@test.com",
                "Rahul",
                "abc 123/+="
        );

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(sender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals("noreply@shopsphere.com", message.getFrom());
        assertArrayEquals(new String[]{"user@test.com"}, message.getTo());
        assertEquals("Verify your email address", message.getSubject());

        assertEquals(
                true,
                message.getText().contains(
                        "http://localhost:3000/verify?token=abc+123%2F%2B%3D"
                )
        );

        assertEquals(true, message.getText().contains("Hi Rahul"));
        assertEquals(true, message.getText().contains("24 hours"));
    }

    @Test
    void sendPasswordResetEmail_sendsCorrectDetails() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        service.sendPasswordResetEmail("user@test.com", "reset-token");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(sender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals("Reset your password", message.getSubject());
        assertEquals("noreply@shopsphere.com", message.getFrom());
        assertArrayEquals(new String[]{"user@test.com"}, message.getTo());

        assertEquals(true, message.getText().contains(
                "http://localhost:3000/reset?token=reset-token"
        ));
        assertEquals(true, message.getText().contains("30 minutes"));
    }

    @Test
    void sendPasswordChangedEmail_sendsCorrectMessage() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        service.sendPasswordChangedEmail("user@test.com");

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(sender).send(captor.capture());

        assertEquals(
                "Your password was changed",
                captor.getValue().getSubject()
        );

        assertEquals(true, captor.getValue().getText().contains(
                "all your sessions were signed out"
        ));
    }

    @Test
    void sendAccountLockedEmail_includesLockExpiry() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        LocalDateTime until = LocalDateTime.of(2026, 11, 6, 15, 30);

        service.sendAccountLockedEmail("user@test.com", until);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(sender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals(
                "Your account is temporarily locked",
                message.getSubject()
        );

        assertEquals(true, message.getText().contains(until + " UTC"));
    }

    @Test
    void sendMethods_whenLogOnlyEnabled_doNotCallSmtp() {
        service = new MailService(senderProvider, properties(true));

        service.sendVerificationEmail("user@test.com", "Rahul", "token");
        service.sendPasswordResetEmail("user@test.com", "token");
        service.sendPasswordChangedEmail("user@test.com");
        service.sendAccountLockedEmail(
                "user@test.com",
                LocalDateTime.of(2026, 11, 6, 15, 30)
        );

        verifyNoInteractions(senderProvider, sender);
    }

    @Test
    void sendMethods_whenSmtpUnavailable_doNotSendEmail() {
        when(senderProvider.getIfAvailable()).thenReturn(null);

        service.sendPasswordChangedEmail("user@test.com");

        verify(senderProvider).getIfAvailable();
        verifyNoInteractions(sender);
    }

    @Test
    void sendMethods_whenSmtpThrowsRuntimeException_exceptionIsHandled() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        doThrow(new RuntimeException("SMTP connection failed"))
                .when(sender).send(any(SimpleMailMessage.class));

        service.sendPasswordChangedEmail("user@test.com");

        verify(sender).send(any(SimpleMailMessage.class));
    }
}
