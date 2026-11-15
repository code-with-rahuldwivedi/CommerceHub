package com.shopsphere.orderservice.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Backlog gauges: alert when orders_outbox_oldest_pending_age_seconds keeps growing or dead > 0. */
@Component
public class OutboxGauges {

    public OutboxGauges(OutboxEventRepository repository, MeterRegistry registry) {
        Gauge.builder("orders.outbox.pending", repository, r -> safe(() -> r.countByStatus(OutboxEvent.PENDING)))
                .description("Outbox events waiting to be published").register(registry);
        Gauge.builder("orders.outbox.processing", repository, r -> safe(() -> r.countByStatus(OutboxEvent.PROCESSING)))
                .description("Outbox events currently claimed by a publisher").register(registry);
        Gauge.builder("orders.outbox.dead.current", repository, r -> safe(() -> r.countByStatus(OutboxEvent.DEAD)))
                .description("Outbox events parked after too many failed attempts").register(registry);
        Gauge.builder("orders.outbox.oldest.pending.age.seconds", repository, OutboxGauges::oldestPendingAgeSeconds)
                .description("Age of the oldest event that is still waiting").register(registry);
    }

    private static double oldestPendingAgeSeconds(OutboxEventRepository repository) {
        try {
            LocalDateTime oldest = repository.findOldestPendingCreatedAt();
            return oldest == null ? 0 : Duration.between(oldest, LocalDateTime.now(ZoneOffset.UTC)).toSeconds();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static double safe(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
