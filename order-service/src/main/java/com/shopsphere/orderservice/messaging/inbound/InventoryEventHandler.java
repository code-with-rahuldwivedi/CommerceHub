package com.shopsphere.orderservice.messaging.inbound;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.messaging.EventTypes;
import com.shopsphere.orderservice.service.OrderLifecycleService;

/** Stock results from inventory-service (envelope form). Payload: {orderId, commandId?, reason?}. */
@Component
public class InventoryEventHandler implements InboundEventHandler {

    private final OrderLifecycleService lifecycle;

    public InventoryEventHandler(OrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(EventTypes.INVENTORY_RESERVED, EventTypes.INVENTORY_RESERVATION_FAILED,
                EventTypes.INVENTORY_RELEASED, EventTypes.INVENTORY_RELEASE_FAILED);
    }

    @Override
    public void handle(String eventType, JsonNode payload, EventContext context) {
        long orderId = Payloads.requirePositiveLong(payload, "orderId");
        String reason = Payloads.optionalText(payload, "reason");

        switch (eventType) {
            case EventTypes.INVENTORY_RESERVED -> lifecycle.onInventoryReserved(orderId);
            case EventTypes.INVENTORY_RESERVATION_FAILED -> lifecycle.onInventoryReservationFailed(orderId, reason);
            case EventTypes.INVENTORY_RELEASED ->
                    lifecycle.onCancellationResult(orderId, Payloads.requireText(payload, "commandId"), true, reason);
            case EventTypes.INVENTORY_RELEASE_FAILED ->
                    lifecycle.onCancellationResult(orderId, Payloads.requireText(payload, "commandId"), false, reason);
            default -> throw new InvalidEventException("Unsupported inventory event: " + eventType);
        }
    }
}
