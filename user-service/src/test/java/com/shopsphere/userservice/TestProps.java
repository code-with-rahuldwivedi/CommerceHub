package com.shopsphere.userservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.shopsphere.userservice.config.AppProperties;

/** Shared fixtures for unit tests. */
public final class TestProps {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private TestProps() {
    }

    public static AppProperties app(boolean requireEmailVerification) {
        return new AppProperties(
                new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppProperties.RateLimit(true, 20),
                new AppProperties.Bootstrap("", "", ""),
                new AppProperties.Account(requireEmailVerification, Duration.ofHours(24), Duration.ofMinutes(30),
                        Duration.ofMinutes(1), "https://app/verify?token={token}", "https://app/reset?token={token}"),
                new AppProperties.Mail("no-reply@test", true),
                new AppProperties.Audit(365),
                new AppProperties.Request(16384));
    }
}
