package com.shopsphere.orderservice.exception;

import java.util.List;

public class ProductUnavailableException extends RuntimeException {

    private final List<Long> productIds;

    public ProductUnavailableException(List<Long> productIds) {
        super("One or more products are not available");
        this.productIds = List.copyOf(productIds);
    }

    public List<Long> getProductIds() {
        return productIds;
    }
}