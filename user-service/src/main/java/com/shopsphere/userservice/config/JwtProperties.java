package com.shopsphere.userservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKey,
        @DefaultValue List<String> previousPublicKeys,
        boolean allowEphemeralKey,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl) {
}
