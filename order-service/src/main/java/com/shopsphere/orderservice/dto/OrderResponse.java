package com.shopsphere.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.shopsphere.orderservice.entity.OrderStatus;

/**
 * "productId" and "quantity" are only filled for single-item orders and exist for older clients; use "items".
 * Timestamps are UTC.
 */
public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        Integer quantity,
        List<OrderItemResponse> items,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal totalPrice,
        OrderStatus status,
        String cancellationReason,
        AddressResponse shippingAddress,
        String contactEmail,
        LocalDateTime orderDate,
        LocalDateTime updatedAt,
        Long version) {
}
