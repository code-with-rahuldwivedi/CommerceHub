package com.shopsphere.orderservice.metrics;

import org.springframework.stereotype.Component;

import com.shopsphere.orderservice.entity.OrderStatus;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Business and messaging counters for dashboards and alerts (/actuator/prometheus):
 * orders_placed_total{outcome}, orders_transitions_total{from,to}, orders_inbound_total{handler,outcome},
 * orders_dead_letter_total{topic}, orders_outbox_*_total{topic}, orders_payment_orphaned_total.
 * Tag values come from fixed sets or configured topic names (bounded cardinality).
 */
@Component
public class OrderMetrics {

    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** outcome: created | replayed */
    public void orderPlaced(String outcome) {
        registry.counter("orders.placed", "outcome", outcome).increment();
    }

    public void transition(OrderStatus from, OrderStatus to) {
        registry.counter("orders.transitions", "from", from == null ? "NONE" : from.name(), "to", to.name()).increment();
    }

    /** outcome: processed | duplicate | ignored */
    public void inbound(String handler, String outcome) {
        registry.counter("orders.inbound", "handler", handler, "outcome", outcome).increment();
    }

    /** A message ended in the dead-letter topic: needs a human. */
    public void deadLetter(String topic) {
        registry.counter("orders.dead.letter", "topic", topic).increment();
    }

    public void outboxPublished(String topic) {
        registry.counter("orders.outbox.published", "topic", topic).increment();
    }

    public void outboxFailed(String topic) {
        registry.counter("orders.outbox.failed", "topic", topic).increment();
    }

    /** An event exhausted its attempts and was parked as DEAD: needs a human. */
    public void outboxDead(String topic) {
        registry.counter("orders.outbox.dead", "topic", topic).increment();
    }

    /** Money was taken for an order that is no longer payable: needs a refund. */
    public void paymentOrphaned() {
        registry.counter("orders.payment.orphaned").increment();
    }
}
