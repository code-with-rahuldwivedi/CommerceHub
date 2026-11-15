package com.shopsphere.orderservice.client;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

/**
 * Passes the request id on to product-service so one customer request can be followed across services.
 * Deliberately NOT a @Configuration class: it only applies to the Feign client that references it.
 * The customer's access token is NOT forwarded: catalog reads are public, and forwarding user tokens
 * to other services would let them act as the user.
 */
public class FeignRequestIdConfig {

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> {
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                template.header("X-Request-Id", requestId);
            }
        };
    }
}
