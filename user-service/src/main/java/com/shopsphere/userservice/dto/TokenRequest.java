package com.shopsphere.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 200, message = "Invalid token")
        String token) {
}
