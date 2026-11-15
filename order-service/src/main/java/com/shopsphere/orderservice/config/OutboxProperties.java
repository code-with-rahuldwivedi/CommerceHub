package com.shopsphere.orderservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(@Valid @NotNull Publisher publisher, @Valid @NotNull Cleanup cleanup) {

    public record Publisher(boolean enabled,
                            @Min(1) long fixedDelayMs,
                            @Min(1) @Max(500) int batchSize,
                            @NotNull Duration staleClaim,
                            @NotNull Duration sendTimeout,
                            @Min(1) int maxAttempts) {
    }

    public record Cleanup(boolean enabled, @NotBlank String cron, @Min(1) int retentionDays) {
    }
}
