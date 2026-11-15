package com.shopsphere.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.shopsphere.orderservice.exception.CatalogUnavailableException;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

class ResilientCatalogGatewayTest {

    private ProductCatalogClient client;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private ResilientCatalogGateway gateway;

    @BeforeEach
    void setUp() {
        client = mock(ProductCatalogClient.class);
        circuitBreaker = CircuitBreaker.of("catalog", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build());
        retry = Retry.of("catalog", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(1))
                .build());
        gateway = new ResilientCatalogGateway(client, circuitBreaker, retry);
    }

    @Test
    void findActiveProducts_filtersInactiveAndDeduplicates() {
        when(client.getProducts("1,2,3")).thenReturn(List.of(
                new CatalogProduct(1L, "Laptop", new BigDecimal("50000"), "ACTIVE"),
                new CatalogProduct(1L, "Laptop Dup", new BigDecimal("50000"), "ACTIVE"),
                new CatalogProduct(2L, "Draft Item", new BigDecimal("100"), "INACTIVE"),
                new CatalogProduct(null, "Broken", new BigDecimal("10"), "ACTIVE")
        ));

        Map<Long, CatalogProduct> result = gateway.findActiveProducts(List.of(1L, 2L, 3L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).name()).isEqualTo("Laptop");
    }

    @Test
    void findActiveProducts_wrapsFeignExceptionAndOpenCircuitBreaker() {
        Request req = Request.create(Request.HttpMethod.GET, "/api/products/batch", Map.of(), null, StandardCharsets.UTF_8, null);
        when(client.getProducts("1")).thenThrow(new FeignException.BadRequest("bad", req, null, null));

        assertThatThrownBy(() -> gateway.findActiveProducts(List.of(1L)))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("400");
        verify(client, times(2)).getProducts("1");

        circuitBreaker.transitionToOpenState();
        assertThatThrownBy(() -> gateway.findActiveProducts(List.of(1L)))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("circuit breaker is open");
    }

    @Test
    void feignRequestIdConfig_propagatesMdcRequestId() {
        FeignRequestIdConfig config = new FeignRequestIdConfig();
        RequestTemplate template = new RequestTemplate();

        MDC.put("requestId", "req-12345678");
        try {
            config.requestIdInterceptor().apply(template);
            assertThat(template.headers().get("X-Request-Id")).containsExactly("req-12345678");
        } finally {
            MDC.remove("requestId");
        }

        RequestTemplate emptyTemplate = new RequestTemplate();
        config.requestIdInterceptor().apply(emptyTemplate);
        assertThat(emptyTemplate.headers()).doesNotContainKey("X-Request-Id");
    }
}