package com.shopsphere.inventoryservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Backward-compatible order command envelope consumed from order-events. */
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalPrice,
        String idempotencyKey,
        LocalDateTime orderDate,
        String eventType,
        String commandId
) {
    public OrderCreatedEvent(
            Long orderId, Long userId, Long productId, Integer quantity,
            BigDecimal totalPrice, String idempotencyKey, LocalDateTime orderDate) {
        this(orderId, userId, productId, quantity, totalPrice, idempotencyKey,
                orderDate, "ORDER_CREATED", null);
    }
}
