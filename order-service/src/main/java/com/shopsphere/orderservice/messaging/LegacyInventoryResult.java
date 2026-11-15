package com.shopsphere.orderservice.messaging;

/** The ORIGINAL result message of the existing inventory-service on "inventory-events". */
public record LegacyInventoryResult(
        Long orderId,
        Long productId,
        Integer quantity,
        String status,
        String message,
        String commandId) {
}
