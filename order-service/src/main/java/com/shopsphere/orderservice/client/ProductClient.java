
package com.shopsphere.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.dto.StockUpdateRequest;

@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductClient {

    @GetMapping("/api/product/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);

    @PostMapping("/api/product/{id}/reduce-stock")
    ProductResponse reduceStock(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String operationKey,
            @RequestBody StockUpdateRequest request
    );

    @PostMapping("/api/product/{id}/restore-stock")
    void restoreStock(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String operationKey,
            @RequestBody StockUpdateRequest request
    );
}
