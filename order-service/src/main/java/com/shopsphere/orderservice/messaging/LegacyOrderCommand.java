package com.shopsphere.orderservice.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The ORIGINAL message shape on the "order-events" topic (field names unchanged), still consumed by the existing
 * inventory-service. Only single-item orders can be expressed in it.
 */
public record LegacyOrderCommand(
        Long orderId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalPrice,
        String idempotencyKey,
        LocalDateTime orderDate,
        String eventType,
        String commandId) {
}
