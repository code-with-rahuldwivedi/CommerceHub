package com.shopsphere.orderservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Prices and names for several products in ONE call. "url" is empty in normal operation (Eureka lookup);
 * set PRODUCT_SERVICE_URL to talk to a fixed address (local development without Eureka).
 */
@FeignClient(
        name = "PRODUCT-SERVICE",
        url = "${orders.catalog.url:}",
        configuration = FeignRequestIdConfig.class)
public interface ProductCatalogClient {

    // Only ACTIVE products are returned; unknown and unpublished ids are simply missing from the result. 
    @GetMapping("/api/products/batch")
    List<CatalogProduct> getProducts(@RequestParam("ids") String commaSeparatedIds);
}
