package com.shopsphere.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import com.shopsphere.orderservice.config.AppConfig;
import com.shopsphere.orderservice.config.KafkaConsumerConfig;
import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.config.PricingConfig;
import com.shopsphere.orderservice.config.ResilienceConfig;
import com.shopsphere.orderservice.config.SecurityConfig;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.service.DefaultPricingPolicy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class OrderSpecificationsAndConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void orderSpecifications_buildsPredicatesForOptionalFilters() {
        Root<Order> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> userPath = mock(Path.class);
        Path<Object> statusPath = mock(Path.class);
        Predicate p1 = mock(Predicate.class);
        Predicate p2 = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);

        when(root.get("userId")).thenReturn(userPath);
        when(root.get("status")).thenReturn(statusPath);
        when(cb.equal(userPath, 10L)).thenReturn(p1);
        when(cb.equal(statusPath, OrderStatus.CONFIRMED)).thenReturn(p2);

        // Handling both vararray overload used by predicates.toArray(...) and direct combination
        when(cb.and(any(Predicate[].class))).thenReturn(combined);
        when(cb.and(p1, p2)).thenReturn(combined);

        // 1. When both filters are provided
        var matchingSpec = OrderSpecifications.matching(10L, OrderStatus.CONFIRMED);
        assertThat(matchingSpec.toPredicate(root, query, cb)).isSameAs(combined);

        // 2. When both filters are null (returns empty criteria conjunction cb.and())
        var emptySpec = OrderSpecifications.matching(null, null);
        assertThat(emptySpec.toPredicate(root, query, cb)).isSameAs(combined);
    }

    @Test
    @SuppressWarnings("unchecked")
    void configBeans_constructCleanly() {
        assertThat(new AppConfig().clock()).isNotNull();
        assertThat(new PricingConfig().defaultPricingPolicy()).isInstanceOf(DefaultPricingPolicy.class);
        assertThat(new SecurityConfig().jwtAuthenticationConverter()).isNotNull();

        OrderProperties props = new OrderProperties(
                "INR", 5, 100, false,
                new OrderProperties.Events("order.events.v1", true, "order-events"),
                new OrderProperties.Payment(false),
                new OrderProperties.Expiry(false, Duration.ofMinutes(15), Duration.ofMinutes(30), 60000),
                new OrderProperties.Inbound(false, "inventory.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(2, Duration.ofMillis(10), 2.0, Duration.ofMillis(50))),
                new OrderProperties.Catalog(3, Duration.ofMillis(100), 10, 50, Duration.ofSeconds(10))
        );

        ResilienceConfig resilienceConfig = new ResilienceConfig();
        var registry = resilienceConfig.circuitBreakerRegistry(props, new SimpleMeterRegistry());
        assertThat(resilienceConfig.catalogCircuitBreaker(registry)).isNotNull();
        assertThat(resilienceConfig.catalogRetry(props)).isNotNull();

        KafkaConsumerConfig kafkaConfig = new KafkaConsumerConfig();
        assertThat(kafkaConfig.inboundErrorHandler(mock(KafkaTemplate.class), props,
                new OrderMetrics(new SimpleMeterRegistry()))).isNotNull();
    }
}