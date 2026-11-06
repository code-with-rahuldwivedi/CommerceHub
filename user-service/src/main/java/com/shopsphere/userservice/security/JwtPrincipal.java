package com.shopsphere.userservice.security;

import java.security.Principal;

import com.shopsphere.userservice.entity.Role;

/** What controllers see for an authenticated request (built from the JWT, no DB hit). */
public record JwtPrincipal(long userId, Role role) implements Principal {

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
