package com.shopsphere.orderservice.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopsphere.orderservice.exception.CatalogUnavailableException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;

/**
 * One batched call, bounded by Feign timeouts, retried on transient failures (a GET is safe to repeat) and guarded
 * by a circuit breaker so a sick product-service fails fast instead of piling up blocked request threads.
 */
@Component
public class ResilientCatalogGateway implements CatalogGateway {

    private static final Logger log = LoggerFactory.getLogger(ResilientCatalogGateway.class);

    private final ProductCatalogClient client;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientCatalogGateway(ProductCatalogClient client, CircuitBreaker catalogCircuitBreaker,
                                   Retry catalogRetry) {
        this.client = client;
        this.circuitBreaker = catalogCircuitBreaker;
        this.retry = catalogRetry;
    }

    @Override
    public Map<Long, CatalogProduct> findActiveProducts(Collection<Long> productIds) {
        String ids = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // Retry wraps the circuit breaker: every attempt is counted by the breaker.
        Supplier<List<CatalogProduct>> call = Decorators
                .ofSupplier(() -> client.getProducts(ids))
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .decorate();

        List<CatalogProduct> products;
        try {
            products = call.get();
        } catch (CallNotPermittedException e) {
            throw new CatalogUnavailableException("Catalog circuit breaker is open", e);
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                log.error("Catalog rejected the request with {} (this is a bug, not an outage)", e.status());
            }
            throw new CatalogUnavailableException("Catalog call failed (status " + e.status() + ")", e);
        }

        return products.stream()
                .filter(p -> p.id() != null && (p.status() == null || "ACTIVE".equals(p.status())))
                .collect(Collectors.toMap(CatalogProduct::id, p -> p, (first, second) -> first));
    }
}
