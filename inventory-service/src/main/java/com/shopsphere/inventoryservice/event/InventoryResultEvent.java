package com.shopsphere.inventoryservice.event;

public record InventoryResultEvent(
        Long orderId,
        Long productId,
        Integer quantity,
        String status,
        String message,
        String commandId
) {
    public InventoryResultEvent(
            Long orderId, Long productId, Integer quantity,
            String status, String message) {
        this(orderId, productId, quantity, status, message, null);
    }
}
