package com.shopsphere.orderservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.event.InventoryResultEvent;
import com.shopsphere.orderservice.repository.OrderRepository;

@Component
public class InventoryResultConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryResultConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;

    public InventoryResultConsumer(
            ObjectMapper objectMapper,
            OrderRepository orderRepository) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
    }

    @KafkaListener(
            topics = "${inventory.kafka.result-topic:inventory-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}"
    )
    @Transactional
    public void consumeInventoryResult(String message) throws Exception {
        final InventoryResultEvent event;

        try {
            event = objectMapper.readValue(message, InventoryResultEvent.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidInventoryResultException(
                    "Malformed inventory result event", exception);
        }

        // Validate the event BEFORE querying the database.
        validate(event, message);

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new MissingOrderException(
                        "Order not found for inventory result: " + event.orderId()));

        if (!order.getProductId().equals(event.productId())
                || !order.getQuantity().equals(event.quantity())) {
            throw new InvalidInventoryResultException(
                    "Inventory result does not match order " + event.orderId());
        }

        switch (event.status()) {
            case "INVENTORY_RESERVED" -> {
                if (order.getStatus() != OrderStatus.PENDING) {
                    log.debug(
                            "Ignoring duplicate/stale inventory result for order {} status {}",
                            order.getId(), order.getStatus());
                    return;
                }
                order.setStatus(OrderStatus.CONFIRMED);
            }

            case "INVENTORY_RESERVATION_FAILED" -> {
                if (order.getStatus() != OrderStatus.PENDING) {
                    log.debug(
                            "Ignoring duplicate/stale inventory result for order {} status {}",
                            order.getId(), order.getStatus());
                    return;
                }
                order.setStatus(OrderStatus.FAILED);
            }

            case "ORDER_CANCELLED", "ORDER_CANCELLATION_FAILED" -> {
                if (order.getStatus() != OrderStatus.CANCEL_REQUESTED
                        || event.commandId() == null
                        || !event.commandId().equals(
                                order.getCancellationCommandId())) {
                    log.debug(
                            "Ignoring stale cancellation result. orderId={}, commandId={}",
                            order.getId(), event.commandId());
                    return;
                }

                order.setStatus(
                        "ORDER_CANCELLED".equals(event.status())
                                ? OrderStatus.CANCELLED
                                : OrderStatus.CANCEL_FAILED);
            }

            default -> throw new InvalidInventoryResultException(
                    "Unknown inventory result status: " + event.status());
        }

        orderRepository.saveAndFlush(order);

        log.info("Order {} status updated to {}",
                order.getId(), order.getStatus());
    }

    private void validate(InventoryResultEvent event, String message) {
        if (event == null
                || event.orderId() == null || event.orderId() <= 0
                || event.productId() == null || event.productId() <= 0
                || event.quantity() == null || event.quantity() <= 0
                || event.status() == null || event.status().isBlank()) {
            throw new InvalidInventoryResultException(
                    "Invalid inventory result event: " + message);
        }

        // Reject unknown statuses before looking up the order.
        if (!"INVENTORY_RESERVED".equals(event.status())
                && !"INVENTORY_RESERVATION_FAILED".equals(event.status())
                && !"ORDER_CANCELLED".equals(event.status())
                && !"ORDER_CANCELLATION_FAILED".equals(event.status())) {
            throw new InvalidInventoryResultException(
                    "Unknown inventory result status: " + event.status());
        }

        if (("ORDER_CANCELLED".equals(event.status())
                || "ORDER_CANCELLATION_FAILED".equals(event.status()))
                && (event.commandId() == null
                || event.commandId().isBlank())) {
            throw new InvalidInventoryResultException(
                    "Cancellation result must include commandId");
        }
    }

    public static class MissingOrderException extends RuntimeException {
        public MissingOrderException(String message) {
            super(message);
        }
    }

    public static class InvalidInventoryResultException extends RuntimeException {
        public InvalidInventoryResultException(String message) {
            super(message);
        }

        public InvalidInventoryResultException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
