package com.shopsphere.orderservice.config;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Forwards the caller's Bearer token to a Feign client.
 *
 * Intentionally NOT annotated with @Configuration. If it were, Spring would
 * component-scan it and apply it to every Feign client (including
 * InventoryClient). It is attached only to the clients that need it via
 * @FeignClient(configuration = FeignAuthConfig.class).
 */
public class FeignAuthConfig {

    @org.springframework.context.annotation.Bean
    public RequestInterceptor authorizationHeaderForwarder() {
        return template -> {
            var attributes =
                    RequestContextHolder.getRequestAttributes();

            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request =
                        servletAttributes.getRequest();

                String authorization =
                        request.getHeader("Authorization");

                if (authorization != null
                        && authorization.startsWith("Bearer ")) {
                    template.header("Authorization", authorization);
                }
            }
        };
    }
}