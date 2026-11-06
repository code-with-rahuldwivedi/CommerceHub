package com.shopsphere.userservice.dto;

import com.shopsphere.userservice.entity.Role;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "role is required")
        Role role) {
}
