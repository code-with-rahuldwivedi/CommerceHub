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

        InventoryResultEvent event;

        try {
            event = objectMapper.readValue(
                    message, InventoryResultEvent.class);
        } catch (JsonProcessingException ex) {
            throw new InvalidInventoryResultException(
                    "Malformed inventory result event", ex);
        }

        if (event.orderId() == null
                || event.orderId() <= 0
                || event.productId() == null
                || event.productId() <= 0
                || event.quantity() == null
                || event.quantity() <= 0
                || event.status() == null
                || event.status().isBlank()) {

            throw new InvalidInventoryResultException(
                    "Invalid inventory result event: " + message);
        }
        
        if (!event.status().equals("INVENTORY_RESERVED")
                && !event.status().equals("INVENTORY_RESERVATION_FAILED")) {
            throw new InvalidInventoryResultException(
                    "Unknown inventory result status: " + event.status());
        }

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new MissingOrderException(
                        "Order not found for inventory result: "
                                + event.orderId()));

        if (!order.getProductId().equals(event.productId())
                || !order.getQuantity().equals(event.quantity())) {
            throw new InvalidInventoryResultException(
                    "Inventory result does not match order "
                            + event.orderId());
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info(
                    "Ignoring inventory result for order {} with status {}",
                    order.getId(), order.getStatus());
            return;
        }

        if ("INVENTORY_RESERVED".equals(event.status())) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            order.setStatus(OrderStatus.FAILED);
        }

        orderRepository.save(order);

        log.info("Order {} status updated to {}",
                order.getId(), order.getStatus());
    }

    public static class MissingOrderException extends RuntimeException {
        public MissingOrderException(String message) {
            super(message);
        }
    }

    public static class InvalidInventoryResultException
            extends RuntimeException {
        public InvalidInventoryResultException(String message) {
            super(message);
        }

        public InvalidInventoryResultException(
                String message, Throwable cause) {
            super(message, cause);
        }
    }
}
