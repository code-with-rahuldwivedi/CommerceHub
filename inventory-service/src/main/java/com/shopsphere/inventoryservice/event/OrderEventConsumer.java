package com.shopsphere.inventoryservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.inventoryservice.service.InventoryResultEventService;
import com.shopsphere.inventoryservice.service.InventoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final InventoryResultEventService resultEventService;

    public OrderEventConsumer(
            ObjectMapper objectMapper,
            InventoryService inventoryService,
            InventoryResultEventService resultEventService) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.resultEventService = resultEventService;
    }

    @KafkaListener(
            topics = "${inventory.kafka.order-topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}"
    )
    public void consumeOrderCommand(String message) throws Exception {
        final OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed order command event", exception);
        }

        validate(event);
        String eventType = event.eventType() == null || event.eventType().isBlank()
                ? "ORDER_CREATED" : event.eventType();

        if ("ORDER_CREATED".equals(eventType)) {
            processReservation(event);
            return;
        }
        if ("ORDER_CANCEL_REQUESTED".equals(eventType)) {
            processCancellation(event);
            return;
        }

        // Unknown event types should not be silently acknowledged. The listener's
        // retry/DLT handler will route this poison message for investigation.
        throw new IllegalArgumentException("Unsupported order event type: " + eventType);
    }

    private void processReservation(OrderCreatedEvent event) {
        String operationKey = "order-" + event.orderId() + "-reduce";
        try {
            inventoryService.reduceStock(event.productId(), event.quantity(), operationKey);
            log.info("Inventory reservation processed. orderId={}, productId={}",
                    event.orderId(), event.productId());
        } catch (ResponseStatusException exception) {
            String reason = exception.getReason() == null ? "" : exception.getReason();
            boolean expectedBusinessFailure =
                    (exception.getStatusCode() == HttpStatus.CONFLICT
                            && reason.startsWith("Insufficient stock for product:"))
                    || (exception.getStatusCode() == HttpStatus.NOT_FOUND
                            && reason.startsWith("Inventory not found for product:"));
            if (expectedBusinessFailure) {
                log.warn("Inventory reservation failed as expected. orderId={}, reason={}",
                        event.orderId(), reason);
                return; // Result is already in the inventory outbox.
            }
            throw exception;
        }
    }

    private void processCancellation(OrderCreatedEvent event) {
        if (event.commandId() == null || event.commandId().isBlank()) {
            throw new IllegalArgumentException("ORDER_CANCEL_REQUESTED requires commandId");
        }

        // The operation key is deliberately stable per order. Replaying the same
        // command after a crash cannot restore stock twice.
        String operationKey = "order-" + event.orderId() + "-restore";
        try {
            inventoryService.restoreStock(event.productId(), event.quantity(), operationKey);
            resultEventService.recordCancellationSuccess(
                    event.orderId(), event.commandId(), event.productId(), event.quantity());
            log.info("Cancellation inventory compensation completed. orderId={}, commandId={}",
                    event.orderId(), event.commandId());
        } catch (ResponseStatusException exception) {
            HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.CONFLICT) {
                resultEventService.recordCancellationFailure(
                        event.orderId(), event.commandId(), event.productId(), event.quantity(),
                        exception.getReason() == null ? "Inventory cancellation failed" : exception.getReason());
                log.warn("Cancellation compensation rejected. orderId={}, commandId={}, status={}",
                        event.orderId(), event.commandId(), status);
                return;
            }
            throw exception;
        }
    }

    private void validate(OrderCreatedEvent event) {
        if (event == null
                || event.orderId() == null || event.orderId() <= 0
                || event.productId() == null || event.productId() <= 0
                || event.quantity() == null || event.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid order event: orderId, productId and quantity must be positive");
        }
    }
}
