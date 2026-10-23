
package com.shopsphere.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

public class InventoryFeignConfig {

    @Bean
    public RequestInterceptor inventoryServiceTokenInterceptor(
            @Value("${inventory.service.token}") String serviceToken) {

        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "Inventory service token is not configured");
        }

        return template ->
                template.header("X-Service-Token", serviceToken);
    }
}
