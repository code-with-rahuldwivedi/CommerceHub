package com.shopsphere.userservice.service;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Security-relevant counters for dashboards and alerts (exposed at /actuator/prometheus):
 * auth_login_total{outcome}, auth_refresh_total{outcome}, auth_lockout_total, auth_refresh_replay_total,
 * auth_ratelimit_total{endpoint}, auth_register_total, auth_account_total{event}.
 * All tag values come from fixed sets (no user input) to keep cardinality bounded.
 */
@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** outcome: success | bad_credentials | rejected | unverified */
    public void login(String outcome) {
        registry.counter("auth.login", "outcome", outcome).increment();
    }

    /** outcome: success | failure */
    public void refresh(String outcome) {
        registry.counter("auth.refresh", "outcome", outcome).increment();
    }

    public void refreshReplay() {
        registry.counter("auth.refresh.replay").increment();
    }

    public void register() {
        registry.counter("auth.register").increment();
    }

    public void lockout() {
        registry.counter("auth.lockout").increment();
    }

    public void rateLimited(String endpoint) {
        registry.counter("auth.ratelimit", "endpoint", endpoint).increment();
    }

    /** event: password_changed | password_reset | email_verified | sessions_revoked */
    public void account(String event) {
        registry.counter("auth.account", "event", event).increment();
    }
}
