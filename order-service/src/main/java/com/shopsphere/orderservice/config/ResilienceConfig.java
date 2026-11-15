package com.shopsphere.orderservice.config;

import java.io.IOException;
import java.util.function.Predicate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;

// Retry + circuit breaker for the synchronous call to product-service. 
@Configuration
public class ResilienceConfig {

    // Only infrastructure problems count: connection errors, timeouts and 5xx. A 4xx is our bug, not an outage. 
    static final Predicate<Throwable> TRANSIENT_FAILURE = throwable ->
            throwable instanceof RetryableException
                    || throwable instanceof IOException
                    || (throwable instanceof FeignException feign && (feign.status() >= 500 || feign.status() < 0));

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(OrderProperties properties, MeterRegistry meterRegistry) {
        OrderProperties.Catalog catalog = properties.catalog();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(catalog.circuitWindow())
                .minimumNumberOfCalls(Math.min(catalog.circuitWindow(), 10))
                .failureRateThreshold(catalog.circuitFailureRate())
                .waitDurationInOpenState(catalog.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(TRANSIENT_FAILURE)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public CircuitBreaker catalogCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("catalog");
    }

    @Bean
    public Retry catalogRetry(OrderProperties properties) {
        OrderProperties.Catalog catalog = properties.catalog();
        return Retry.of("catalog", RetryConfig.custom()
                .maxAttempts(catalog.retryAttempts())
                .waitDuration(catalog.retryWait())
                .retryOnException(TRANSIENT_FAILURE)
                .build());
    }
}