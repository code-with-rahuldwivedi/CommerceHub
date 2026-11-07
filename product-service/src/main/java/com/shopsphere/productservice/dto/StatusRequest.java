package com.shopsphere.productservice.dto;

import com.shopsphere.productservice.entity.ProductStatus;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @NotNull(message = "status is required")
        ProductStatus status) {
}
