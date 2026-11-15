package com.shopsphere.orderservice.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricsAndGaugesTest {

    @Test
    void orderMetrics_incrementsAllCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderMetrics metrics = new OrderMetrics(registry);

        metrics.orderPlaced("created");
        metrics.transition(null, OrderStatus.PENDING);
        metrics.transition(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        metrics.inbound("inventory", "processed");
        metrics.deadLetter("inventory.events.v1");
        metrics.outboxPublished("order.events.v1");
        metrics.outboxFailed("order.events.v1");
        metrics.outboxDead("order.events.v1");
        metrics.paymentOrphaned();

        assertThat(registry.get("orders.placed").tag("outcome", "created").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.transitions").tag("from", "NONE").tag("to", "PENDING").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.inbound").tag("handler", "inventory").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.dead.letter").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.outbox.published").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.outbox.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.outbox.dead").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("orders.payment.orphaned").counter().count()).isEqualTo(1.0);
    }

    @Test
    void outboxGauges_readsCountsAndOldestAgeSafely() {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        when(repo.countByStatus(OutboxEvent.PENDING)).thenReturn(4L);
        when(repo.countByStatus(OutboxEvent.PROCESSING)).thenReturn(1L);
        when(repo.countByStatus(OutboxEvent.DEAD)).thenReturn(2L);
        when(repo.findOldestPendingCreatedAt()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxGauges(repo, registry);

        assertThat(registry.get("orders.outbox.pending").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("orders.outbox.processing").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("orders.outbox.dead.current").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("orders.outbox.oldest.pending.age.seconds").gauge().value()).isGreaterThanOrEqualTo(29.0);

        when(repo.countByStatus(OutboxEvent.PENDING)).thenThrow(new RuntimeException("DB error"));
        assertThat(registry.get("orders.outbox.pending").gauge().value()).isNaN();
    }
}
