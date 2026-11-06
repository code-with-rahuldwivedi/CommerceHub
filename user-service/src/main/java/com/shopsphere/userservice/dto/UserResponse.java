package com.shopsphere.userservice.dto;

import java.time.LocalDateTime;

import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;

public record UserResponse(Long id, String name, String email, Role role,
                           boolean emailVerified, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.isEmailVerified(), user.getCreatedAt());
    }
}
