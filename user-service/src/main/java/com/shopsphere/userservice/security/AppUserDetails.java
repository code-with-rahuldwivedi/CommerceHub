package com.shopsphere.userservice.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.shopsphere.userservice.entity.Role;

/** Carries id, role and flags so login needs no second DB lookup. */
public final class AppUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;
    private final int failedAttempts;
    private final boolean emailVerified;

    public AppUserDetails(long id, String email, String passwordHash, Role role,
                          boolean enabled, boolean locked, int failedAttempts, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.locked = locked;
        this.failedAttempts = failedAttempts;
        this.emailVerified = emailVerified;
    }

    public long getId() { return id; }
    public Role getRole() { return role; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
