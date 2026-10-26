package com.shopsphere.inventoryservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.inventoryservice.service.InventoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    public OrderEventConsumer(
            ObjectMapper objectMapper,
            InventoryService inventoryService) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    @Value("${inventory.kafka.order-topic:order-events}")
    private String orderTopic;

    @KafkaListener(
            topics = "${inventory.kafka.order-topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}"
    )
    public void consumeOrderCreated(String message) throws Exception {

        OrderCreatedEvent event =
                objectMapper.readValue(message, OrderCreatedEvent.class);

        if (event.orderId() == null
                || event.orderId() <= 0
                || event.productId() == null
                || event.productId() <= 0
                || event.quantity() == null
                || event.quantity() <= 0) {

            throw new IllegalArgumentException(
                    "Invalid order event: orderId, productId and quantity must be positive.");
        }

        String operationKey = "order-" + event.orderId() + "-reduce";

        log.info(
                "Received order event. orderId={}, productId={}, quantity={}",
                event.orderId(),
                event.productId(),
                event.quantity()
        );

        try {
            inventoryService.reduceStock(
                    event.productId(),
                    event.quantity(),
                    operationKey
            );

            log.info(
                    "Inventory reservation processed. orderId={}, productId={}",
                    event.orderId(),
                    event.productId()
            );

        } catch (ResponseStatusException exception) {

            String reason = exception.getReason() == null
                    ? ""
                    : exception.getReason();

            boolean expectedStockFailure =
                    (exception.getStatusCode() == HttpStatus.CONFLICT
                            && reason.startsWith("Insufficient stock for product:"))
                    || (exception.getStatusCode() == HttpStatus.NOT_FOUND
                            && reason.startsWith("Inventory not found for product:"));

            if (expectedStockFailure) {
                // The failure result should already have been saved
                // in the inventory outbox by InventoryTransactionService.
                log.warn(
                        "Inventory reservation failed as expected. orderId={}, reason={}",
                        event.orderId(),
                        reason
                );

                // Return normally so this expected business failure
                // does not trigger repeated Kafka delivery.
                return;
            }

            // Do not silently ignore other errors, such as an
            // idempotency-key conflict or invalid request.
            throw exception;
        }
    }
}
