package com.shopsphere.productservice.dto;

import java.math.BigDecimal;

import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.ProductStatus;

/** Search / paging parameters of GET /api/products. */
public record ProductQuery(
        String text,
        Category category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        int page,
        int size,
        String sort) {
}
