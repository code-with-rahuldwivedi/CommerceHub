package com.shopsphere.productservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record SecurityJwtProperties(
        @NotBlank String jwksUri,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
