package com.shopsphere.productservice.dto;

import java.math.BigDecimal;

import com.shopsphere.productservice.entity.Category;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload. On update, "version" is optional: when present it must equal the
 * product's current version (copy it from the last response), otherwise the update is rejected with 409.
 */
public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name must not exceed 150 characters")
        String name,

        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimals")
        BigDecimal price,

        @NotNull(message = "Category is required")
        Category category,

        @Size(max = 2048, message = "Image URL must not exceed 2048 characters")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Image URL must be an http(s) URL")
        String imageUrl,

        Long version) {
}
