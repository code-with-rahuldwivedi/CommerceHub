package com.shopsphere.userservice.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.util.LogSanitizer;

import jakarta.annotation.PostConstruct;

/**
 * Sends transactional e-mails asynchronously so SMTP latency/outages never block or fail a request.
 * SMTP is configured through SPRING_MAIL_* environment variables. Without SMTP, e-mails are NOT
 * sent (an error is logged); MAIL_LOG_ONLY=true logs them instead and is for development only
 * because the log then contains the secret links.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> senderProvider, AppProperties props) {
        this.senderProvider = senderProvider;
        this.props = props;
    }

    @PostConstruct
    void warnIfNotConfigured() {
        if (props.mail().logOnly()) {
            log.warn("app.mail.log-only is ON: e-mails are written to the log (including secret links). "
                    + "Development only.");
        } else if (senderProvider.getIfAvailable() == null) {
            log.warn("No SMTP configured (SPRING_MAIL_HOST): verification / password-reset e-mails cannot be sent.");
        }
    }

    @Async
    public void sendVerificationEmail(String to, String name, String rawToken) {
        String link = props.account().verifyUrl().replace("{token}", encode(rawToken));
        send(to, "Verify your email address",
                "Hi " + name + ",\n\nPlease verify your email address by opening this link:\n" + link
                        + "\n\nThe link is valid for " + props.account().verificationTtl().toHours() + " hours."
                        + "\nIf you did not create an account, you can ignore this email.\n");
    }

    @Async
    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = props.account().resetUrl().replace("{token}", encode(rawToken));
        send(to, "Reset your password",
                "We received a request to reset your password. Open this link to choose a new one:\n" + link
                        + "\n\nThe link is valid for " + props.account().passwordResetTtl().toMinutes() + " minutes."
                        + "\nIf you did not request this, you can ignore this email; your password is unchanged.\n");
    }

    @Async
    public void sendPasswordChangedEmail(String to) {
        send(to, "Your password was changed",
                "The password of your account was just changed and all your sessions were signed out."
                        + "\nIf this was not you, reset your password immediately and contact support.\n");
    }

    @Async
    public void sendAccountLockedEmail(String to, LocalDateTime until) {
        send(to, "Your account is temporarily locked",
                "Your account was temporarily locked after too many failed sign-in attempts (until "
                        + until + " UTC).\nIf this was not you, reset your password.\n");
    }

    private void send(String to, String subject, String body) {
        try {
            if (props.mail().logOnly()) {
                log.warn("[MAIL_LOG_ONLY] to={} subject={}\n{}", LogSanitizer.maskEmail(to), subject, body);
                return;
            }
            JavaMailSender sender = senderProvider.getIfAvailable();
            if (sender == null) {
                log.error("E-mail '{}' to {} NOT sent: no SMTP configured", subject, LogSanitizer.maskEmail(to));
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.mail().from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("Failed to send e-mail '{}' to {}", subject, LogSanitizer.maskEmail(to), e);
        }
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
