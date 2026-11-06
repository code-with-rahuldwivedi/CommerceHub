package com.shopsphere.userservice.dto;

import java.time.LocalDateTime;

import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;

public record AdminUserResponse(Long id, String name, String email, Role role, boolean enabled,
                                boolean emailVerified, boolean locked, LocalDateTime lockedUntil,
                                LocalDateTime createdAt) {

    public static AdminUserResponse from(User user, LocalDateTime now) {
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.isEnabled(), user.isEmailVerified(), user.isLockedAt(now),
                user.getLockedUntil(), user.getCreatedAt());
    }
}
