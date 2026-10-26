package com.shopsphere.orderservice.event;

public record InventoryResultEvent(
        Long orderId,
        Long productId,
        Integer quantity,
        String status,
        String message
) {
}
