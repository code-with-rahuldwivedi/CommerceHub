package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.dto.OrderResponse;

/** "replayed" is true when the Idempotency-Key had already created this order. */
public record PlacementResult(OrderResponse order, boolean replayed) {
}
