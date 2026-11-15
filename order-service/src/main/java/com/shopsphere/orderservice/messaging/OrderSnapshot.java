package com.shopsphere.orderservice.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full state of the order at the moment of the event ("fat event"). Consumers (payment, shipping, e-mail,
 * analytics, ...) get everything they need without calling order-service back.
 */
public record OrderSnapshot(
        Long orderId,
        Long userId,
        String status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        List<Line> items,
        ShippingAddress shippingAddress,
        String contactEmail,
        Instant placedAt,
        String cancellationCommandId,
        String reason) {

    public record Line(Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record ShippingAddress(String fullName, String line1, String line2, String city, String state,
                                  String postalCode, String country, String phone) {
    }
}
