package com.shopsphere.inventoryservice.service;

public record ClaimedInventoryOutboxEvent(
        Long id,
        Long orderId,
        String payload,
        int attempts
) {
}