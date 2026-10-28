package com.shopsphere.inventoryservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;
import com.shopsphere.inventoryservice.event.InventoryResultEvent;
import com.shopsphere.inventoryservice.repository.InventoryOutboxRepository;

@Service
public class InventoryResultEventService {

    private static final String SUCCESS_EVENT_TYPE = "INVENTORY_RESERVED";
    private static final String FAILURE_EVENT_TYPE = "INVENTORY_RESERVATION_FAILED";

    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryResultEventService(
            InventoryOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReservationSuccess(
            Long orderId, Long productId, Integer quantity) {
        if (outboxRepository.existsByOrderIdAndEventType(orderId, SUCCESS_EVENT_TYPE)) {
            return;
        }
        saveOutboxEvent(orderId, SUCCESS_EVENT_TYPE, new InventoryResultEvent(
                orderId, productId, quantity, SUCCESS_EVENT_TYPE,
                "Inventory reserved successfully", null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReservationFailure(
            Long orderId, Long productId, Integer quantity, String message) {
        if (outboxRepository.existsByOrderIdAndEventType(orderId, FAILURE_EVENT_TYPE)) {
            return;
        }
        saveOutboxEvent(orderId, FAILURE_EVENT_TYPE, new InventoryResultEvent(
                orderId, productId, quantity, FAILURE_EVENT_TYPE, message, null));
    }

    /** Result events use a per-command event type so a later retry attempt can emit its own result. */
    @Transactional
    public void recordCancellationSuccess(
            Long orderId, String commandId, Long productId, Integer quantity) {
        String eventType = cancellationOutboxType("ORDER_CANCELLED", commandId);
        if (outboxRepository.existsByOrderIdAndEventType(orderId, eventType)) {
            return;
        }
        saveOutboxEvent(orderId, eventType, new InventoryResultEvent(
                orderId, productId, quantity, "ORDER_CANCELLED",
                "Inventory restored successfully", commandId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCancellationFailure(
            Long orderId, String commandId, Long productId, Integer quantity, String message) {
        String eventType = cancellationOutboxType("ORDER_CANCELLATION_FAILED", commandId);
        if (outboxRepository.existsByOrderIdAndEventType(orderId, eventType)) {
            return;
        }
        saveOutboxEvent(orderId, eventType, new InventoryResultEvent(
                orderId, productId, quantity, "ORDER_CANCELLATION_FAILED",
                message, commandId));
    }

    private String cancellationOutboxType(String prefix, String commandId) {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId is required for cancellation results");
        }
        return prefix + ":" + commandId;
    }

    private void saveOutboxEvent(
            Long orderId, String eventType, InventoryResultEvent result) {
        InventoryOutboxEvent outboxEvent = new InventoryOutboxEvent();
        outboxEvent.setOrderId(orderId);
        outboxEvent.setEventType(eventType);
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize inventory result event", exception);
        }
        outboxEvent.setStatus("PENDING");
        outboxEvent.setAttempts(0);
        outboxRepository.save(outboxEvent);
    }
}
