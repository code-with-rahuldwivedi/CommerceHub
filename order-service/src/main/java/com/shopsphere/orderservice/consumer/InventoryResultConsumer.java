package com.shopsphere.orderservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.exception.MissingOrderException;
import com.shopsphere.orderservice.messaging.LegacyInventoryResult;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.service.OrderLifecycleService;

/**
 * Consumes the ORIGINAL result messages of the existing inventory-service ("inventory-events", unchanged format)
 * and applies them through {@link OrderLifecycleService}. Always on. Retire it once inventory-service publishes
 * envelope events to "inventory.events.v1" (see docs/EVENTS.md).
 */
@Component
public class InventoryResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryResultConsumer.class);

    static final String RESERVED = "INVENTORY_RESERVED";
    static final String RESERVATION_FAILED = "INVENTORY_RESERVATION_FAILED";
    static final String CANCELLED = "ORDER_CANCELLED";
    static final String CANCELLATION_FAILED = "ORDER_CANCELLATION_FAILED";

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OrderLifecycleService lifecycle;

    public InventoryResultConsumer(ObjectMapper objectMapper, OrderRepository orderRepository,
                                   OrderLifecycleService lifecycle) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.lifecycle = lifecycle;
    }

    @KafkaListener(
            topics = "${orders.inbound.legacy-inventory-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeInventoryResult(String message) {
        LegacyInventoryResult event = parse(message);
        validate(event, message); // before touching the database

        Order order = orderRepository.findWithItemsById(event.orderId())
                .orElseThrow(() -> new MissingOrderException(event.orderId()));

        // This format can only describe single-item orders: the result must match the order's one line.
        if (order.getItems().size() != 1
                || !order.getItems().get(0).getProductId().equals(event.productId())
                || order.getItems().get(0).getQuantity() != event.quantity()) {
            throw new InvalidEventException("Inventory result does not match order " + event.orderId());
        }

        switch (event.status()) {
            case RESERVED -> lifecycle.onInventoryReserved(event.orderId());
            case RESERVATION_FAILED -> lifecycle.onInventoryReservationFailed(event.orderId(), event.message());
            case CANCELLED -> lifecycle.onCancellationResult(event.orderId(), event.commandId(), true, event.message());
            case CANCELLATION_FAILED ->
                    lifecycle.onCancellationResult(event.orderId(), event.commandId(), false, event.message());
            default -> throw new InvalidEventException("Unknown inventory result status: " + event.status());
        }
        log.debug("Applied legacy inventory result {} to order {}", event.status(), event.orderId());
    }

    private LegacyInventoryResult parse(String message) {
        try {
            return objectMapper.readValue(message, LegacyInventoryResult.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("Malformed inventory result event", e);
        }
    }

    private static void validate(LegacyInventoryResult event, String message) {
        if (event == null
                || event.orderId() == null || event.orderId() <= 0
                || event.productId() == null || event.productId() <= 0
                || event.quantity() == null || event.quantity() <= 0
                || event.status() == null || event.status().isBlank()) {
            throw new InvalidEventException("Invalid inventory result event: " + message);
        }
        boolean cancellation = CANCELLED.equals(event.status()) || CANCELLATION_FAILED.equals(event.status());
        if (!RESERVED.equals(event.status()) && !RESERVATION_FAILED.equals(event.status()) && !cancellation) {
            throw new InvalidEventException("Unknown inventory result status: " + event.status());
        }
        if (cancellation && (event.commandId() == null || event.commandId().isBlank())) {
            throw new InvalidEventException("Cancellation result must include commandId");
        }
    }
}
