package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.AppUserDetails;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public CustomUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AppUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.isEnabled(),
                user.isLockedAt(LocalDateTime.now(clock)),
                user.getFailedAttempts(),
                user.isEmailVerified());
    }
}
