package com.shopsphere.inventoryservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal totalPrice,
        String idempotencyKey,
        LocalDateTime orderDate
) {
}
