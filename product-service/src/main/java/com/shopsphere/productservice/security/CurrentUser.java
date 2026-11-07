package com.shopsphere.productservice.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who is calling: user id (null for anonymous) and whether they are an administrator. */
public record CurrentUser(Long userId, boolean admin) {

    public static CurrentUser anonymous() {
        return new CurrentUser(null, false);
    }

    public static CurrentUser fromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return anonymous();
        }

        Long userId = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException ignored) {
                // the decoder already enforces a numeric subject; stay anonymous-safe anyway
            }
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new CurrentUser(userId, admin);
    }
}
