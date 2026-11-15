package com.shopsphere.orderservice.dto;

import jakarta.validation.constraints.Size;

public record CancelRequest(
        @Size(max = 200, message = "Reason must not exceed 200 characters")
        String reason) {
}
