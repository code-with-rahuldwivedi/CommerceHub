package com.shopsphere.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 200, message = "Invalid refresh token")
        String refreshToken) {
}
