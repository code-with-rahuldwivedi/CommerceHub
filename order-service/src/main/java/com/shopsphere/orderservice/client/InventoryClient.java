package com.shopsphere.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.shopsphere.orderservice.config.InventoryFeignConfig;
import com.shopsphere.orderservice.dto.StockUpdateRequest;

@FeignClient(
        name = "INVENTORY-SERVICE",
        configuration = InventoryFeignConfig.class
)
public interface InventoryClient {

    @PostMapping("/api/inventory/{id}/reduce")
    void reduceStock(
            @PathVariable("id") Long productId,
            @RequestHeader("Idempotency-Key") String operationKey,
            @RequestBody StockUpdateRequest request
    );

    @PostMapping("/api/inventory/{id}/restore")
    void restoreStock(
            @PathVariable("id") Long productId,
            @RequestHeader("Idempotency-Key") String operationKey,
            @RequestBody StockUpdateRequest request
    );
}
