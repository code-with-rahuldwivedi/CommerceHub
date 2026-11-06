package com.shopsphere.userservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull RateLimit rateLimit,
        @NotNull Bootstrap bootstrap,
        @Valid @NotNull Account account,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Audit audit,
        @Valid @NotNull Request request) {

    public record Lockout(@Min(1) int maxAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(boolean enabled, @Min(1) int authRequestsPerMinute) {
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
    }

    public record Account(boolean requireEmailVerification,
                          @NotNull Duration verificationTtl,
                          @NotNull Duration passwordResetTtl,
                          @NotNull Duration resendCooldown,
                          @NotBlank String verifyUrl,
                          @NotBlank String resetUrl) {
    }

    public record Mail(@NotBlank String from, boolean logOnly) {
    }

    public record Audit(@Min(1) int retentionDays) {
    }

    public record Request(@Min(1024) long maxBodyBytes) {
    }
}
