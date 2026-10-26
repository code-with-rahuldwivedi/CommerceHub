package com.shopsphere.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEventPayload(
        Long orderId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalPrice,
        String idempotencyKey,
        LocalDateTime orderDate
) {
}
