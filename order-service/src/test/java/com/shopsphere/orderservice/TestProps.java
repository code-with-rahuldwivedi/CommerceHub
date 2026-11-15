package com.shopsphere.orderservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.config.OutboxProperties;

/** Shared fixtures for unit tests. */
public final class TestProps {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private TestProps() {
    }

    public static OrderProperties orders(int maxItems, boolean paymentEnabled, boolean legacyEvents) {
        return new OrderProperties(
                "INR", maxItems, 100, false,
                new OrderProperties.Events("order.events.v1", legacyEvents, "order-events"),
                new OrderProperties.Payment(paymentEnabled),
                new OrderProperties.Expiry(false, Duration.ofMinutes(15), Duration.ofMinutes(30), 60_000),
                new OrderProperties.Inbound(false, "inventory.events.v1,payment.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(2, Duration.ofMillis(50), 1.0, Duration.ofMillis(100))),
                new OrderProperties.Catalog(2, Duration.ofMillis(1), 4, 50, Duration.ofSeconds(60)));
    }

    public static OrderProperties orders() {
        return orders(1, false, true);
    }

    public static OutboxProperties outbox(int maxAttempts) {
        return new OutboxProperties(
                new OutboxProperties.Publisher(true, 500, 50, Duration.ofMinutes(2), Duration.ofSeconds(10), maxAttempts),
                new OutboxProperties.Cleanup(true, "0 15 3 * * *", 7));
    }
}