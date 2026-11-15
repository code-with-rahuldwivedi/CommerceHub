package com.shopsphere.orderservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Either "items" (several lines) or the original single-line form "productId" + "quantity" (kept for
 * existing clients). The customer is always the authenticated user; a "userId" in the body is ignored.
 */
public record OrderRequest(
        @Positive(message = "Product ID must be positive")
        Long productId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity is too large")
        Integer quantity,

        @Size(max = 100, message = "Too many items")
        List<@Valid OrderItemRequest> items,

        @Valid
        AddressRequest shippingAddress,

        @Email(message = "Invalid contact email")
        @Size(max = 254, message = "Contact email must not exceed 254 characters")
        String contactEmail) {
}
