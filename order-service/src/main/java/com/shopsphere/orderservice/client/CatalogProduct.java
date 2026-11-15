package com.shopsphere.orderservice.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// The few product fields an order needs,
//everything else in product-service's response is ignored. 
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProduct(Long id, String name, BigDecimal price, String status) {
}
