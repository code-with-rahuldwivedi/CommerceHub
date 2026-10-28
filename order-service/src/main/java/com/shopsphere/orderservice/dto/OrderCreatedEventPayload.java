package com.shopsphere.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka command envelope. The extra fields are optional when reading older
 * ORDER_CREATED messages written before eventType/commandId were introduced.
 */
public record OrderCreatedEventPayload(
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
    public OrderCreatedEventPayload(
            Long orderId, Long userId, Long productId, Integer quantity,
            BigDecimal totalPrice, String idempotencyKey, LocalDateTime orderDate) {
        this(orderId, userId, productId, quantity, totalPrice, idempotencyKey,
                orderDate, "ORDER_CREATED", null);
    }
}
