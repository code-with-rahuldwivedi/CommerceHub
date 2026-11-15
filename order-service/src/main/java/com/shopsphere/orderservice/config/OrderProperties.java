package com.shopsphere.orderservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Business and integration switches of the order service (prefix "orders"). 
@Validated
@ConfigurationProperties(prefix = "orders")
public record OrderProperties(
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Min(1) @Max(100) int maxItems,
        @Min(1) @Max(1000) int maxQuantityPerItem,
        boolean requireShippingAddress,
        @Valid @NotNull Events events,
        @Valid @NotNull Payment payment,
        @Valid @NotNull Expiry expiry,
        @Valid @NotNull Inbound inbound,
        @Valid @NotNull Catalog catalog) {

    // Outbound streams. "topic" carries versioned envelope events; "legacy" keeps the original single-item commands.  
    public record Events(@NotBlank String topic, boolean legacyEnabled, @NotBlank String legacyTopic) {
    }

    public record Payment(boolean enabled) {
    }

    public record Expiry(boolean enabled,
                         @NotNull Duration pendingTimeout,
                         @NotNull Duration awaitingPaymentTimeout,
                         @Min(1000) long intervalMs) {
    }

    public record Inbound(boolean enabled,
                          @NotBlank String topics,
                          @NotBlank String legacyInventoryTopic,
                          @Valid @NotNull Retry retry) {

        public record Retry(@Min(0) int maxRetries,
                            @NotNull Duration initialInterval,
                            @Positive double multiplier,
                            @NotNull Duration maxInterval) {
        }
    }

    // Synchronous calls to product-service. 
    public record Catalog(@Min(1) int retryAttempts,
                          @NotNull Duration retryWait,
                          @Min(2) int circuitWindow,
                          @Min(1) @Max(100) int circuitFailureRate,
                          @NotNull Duration circuitOpenDuration) {
    }
}
