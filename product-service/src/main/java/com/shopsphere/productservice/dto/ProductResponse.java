package com.shopsphere.productservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;

/** Timestamps are UTC. "version" is what you send back as "version" when updating. */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Category category,
        ProductStatus status,
        String imageUrl,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getStatus(),
                product.getImageUrl(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
