package com.shopsphere.cloud.config;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * One circuit breaker per downstream service (the id used in the route's CircuitBreaker filter).
 * Configured in code so the settings do not depend on a particular property binder being on the classpath.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerCustomizer(GatewayProperties properties) {
        GatewayProperties.Resilience r = properties.resilience();

        CircuitBreakerConfig breaker = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(r.windowSize())
                .minimumNumberOfCalls(r.minimumCalls())
                .failureRateThreshold(r.failureRatePercent())
                .waitDurationInOpenState(r.openDuration())
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(breaker)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(r.timeouts().getOrDefault(id, r.defaultTimeout()))
                        .cancelRunningFuture(true)
                        .build())
                .build());
    }
}
