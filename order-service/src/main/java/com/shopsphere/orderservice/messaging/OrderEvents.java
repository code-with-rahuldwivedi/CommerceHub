package com.shopsphere.orderservice.messaging;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.messaging.outbox.OutboxWriter;

/**
 * Turns an order state change into outbox messages (call inside the transaction that changed the order):
 * <ul>
 *   <li>always one envelope event on the versioned topic (the contract for every present and future consumer);</li>
 *   <li>additionally, while "orders.events.legacy-enabled" is true, the original ORDER_CREATED /
 *       ORDER_CANCEL_REQUESTED command for single-item orders (existing inventory-service).</li>
 * </ul>
 */
@Component
public class OrderEvents {

    static final String PRODUCER = "order-service";
    static final int EVENT_VERSION = 1;

    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderEvents(OutboxWriter outbox, ObjectMapper objectMapper, OrderProperties properties, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** The order's lines must be loaded. */
    public void publish(Order order, String eventType, String reason) {
        String key = String.valueOf(order.getId());

        EventEnvelope<OrderSnapshot> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                EVENT_VERSION,
                clock.instant(),
                PRODUCER,
                "ORDER",
                key,
                MDC.get("requestId"),
                snapshot(order, reason));
        outbox.enqueue(properties.events().topic(), key, eventType, envelope.eventId(), order.getId(), toJson(envelope));

        publishLegacy(order, eventType, key);
    }

    private void publishLegacy(Order order, String eventType, String key) {
        if (!properties.events().legacyEnabled() || order.getItems().size() != 1) {
            return;
        }
        String legacyType;
        if (EventTypes.ORDER_PLACED.equals(eventType)) {
            legacyType = EventTypes.LEGACY_ORDER_CREATED;
        } else if (EventTypes.ORDER_CANCEL_REQUESTED.equals(eventType)) {
            legacyType = EventTypes.ORDER_CANCEL_REQUESTED;
        } else {
            return; // the legacy stream only ever carried these two commands
        }
        OrderItem item = order.getItems().get(0);
        LegacyOrderCommand command = new LegacyOrderCommand(
                order.getId(),
                order.getUserId(),
                item.getProductId(),
                item.getQuantity(),
                order.getTotalPrice(),
                order.getIdempotencyKey(),
                order.getOrderDate(),
                legacyType,
                EventTypes.ORDER_CANCEL_REQUESTED.equals(legacyType) ? order.getCancellationCommandId() : null);
        outbox.enqueue(properties.events().legacyTopic(), key, legacyType, UUID.randomUUID().toString(),
                order.getId(), toJson(command));
    }

    static OrderSnapshot snapshot(Order order, String reason) {
        List<OrderSnapshot.Line> lines = order.getItems().stream()
                .map(i -> new OrderSnapshot.Line(i.getProductId(), i.getProductName(), i.getUnitPrice(),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        Address a = order.getShippingAddress();
        OrderSnapshot.ShippingAddress address = a == null ? null
                : new OrderSnapshot.ShippingAddress(a.getFullName(), a.getLine1(), a.getLine2(), a.getCity(),
                        a.getState(), a.getPostalCode(), a.getCountry(), a.getPhone());
        return new OrderSnapshot(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                lines,
                address,
                order.getContactEmail(),
                order.getOrderDate().toInstant(ZoneOffset.UTC),
                order.getCancellationCommandId(),
                reason);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
