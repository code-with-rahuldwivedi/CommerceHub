package com.shopsphere.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shopSphereOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere - Order Service API")
                        .description("Microservice backend for managing orders, inventory events, and payment lifecycles.")
                        .version("v0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("Rahul Dwivedi")
                                .email("developer@shopsphere.com")));
    }
}