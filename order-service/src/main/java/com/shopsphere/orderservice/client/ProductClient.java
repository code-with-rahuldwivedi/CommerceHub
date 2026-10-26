package com.shopsphere.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.shopsphere.orderservice.config.FeignAuthConfig;
import com.shopsphere.orderservice.dto.ProductResponse;

@FeignClient(
        name = "PRODUCT-SERVICE",
        configuration = FeignAuthConfig.class
)
public interface ProductClient {

    @GetMapping("/api/product/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);
}