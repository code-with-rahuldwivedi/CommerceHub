package com.shopsphere.cloud.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @Valid @NotNull Cors cors,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Request request,
        @Valid @NotNull Resilience resilience) {

    /** Exact origins allowed to call the API from a browser. Empty = no cross-origin access. */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    /**
     * Per-instance token buckets. Multiply by the number of gateway replicas for the effective cluster limit,
     * or move to a shared store (Redis) if you need an exact global limit.
     */
    public record RateLimit(boolean enabled,
                            @Min(1) int authPerMinute,
                            @Min(1) int anonymousPerMinute,
                            @Min(1) int userPerMinute,
                            @Min(1000) long maxKeys) {
    }

    public record Request(@Min(1024) long maxBodyBytes, boolean rejectChunked) {
    }

    public record Resilience(@Min(2) int windowSize,
                             @Min(1) int minimumCalls,
                             @Min(1) @Max(100) int failureRatePercent,
                             @NotNull Duration openDuration,
                             @NotNull Duration defaultTimeout,
                             @DefaultValue Map<String, Duration> timeouts) {
    }
}
