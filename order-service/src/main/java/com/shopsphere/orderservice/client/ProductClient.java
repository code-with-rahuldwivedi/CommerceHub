package com.shopsphere.orderservice.client;

import com.shopsphere.orderservice.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductClient {

    @GetMapping("/api/product/{id}")
    ProductResponse getProductById(@PathVariable Long id);
}