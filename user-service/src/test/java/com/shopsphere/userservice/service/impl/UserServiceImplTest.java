package com.shopsphere.userservice.service.impl;

import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.EmailAlreadyExistsException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User existingUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("Rahul Dwivedi", "rahul@example.com", "password123", null);
        loginRequest = new LoginRequest("rahul@example.com", "password123");

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setName("Rahul Dwivedi");
        existingUser.setEmail("rahul@example.com");
        existingUser.setPassword("encodedPassword");
        existingUser.setRole(Role.USER);
    }

    // ---------- registerUser() tests ----------

    @Test
    void registerUser_ShouldThrowException_WhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.registerUser(registerRequest));

        // Ensure we never try to save a duplicate user
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_ShouldEncodePasswordAndSaveUser_WhenEmailIsNew() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");

        String result = userService.registerUser(registerRequest);

        assertEquals("User Registered Successfully", result);
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_ShouldDefaultRoleToUser_WhenRoleIsNull() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword123");

        userService.registerUser(registerRequest); // role is null in setUp()

        verify(userRepository).save(argThat(user -> user.getRole() == Role.USER));
    }

    @Test
    void registerUser_ShouldKeepRequestedRole_WhenRoleIsProvided() {
        RegisterRequest adminRequest =
                new RegisterRequest("Admin User", "admin@example.com", "adminPass1", Role.ADMIN);

        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedAdminPass");

        userService.registerUser(adminRequest);

        verify(userRepository).save(argThat(user -> user.getRole() == Role.ADMIN));
    }

    // ---------- login() tests ----------

    @Test
    void login_ShouldReturnJwtToken_WhenCredentialsAreValid() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(existingUser)).thenReturn("fake-jwt-token");

        String token = userService.login(loginRequest);

        assertEquals("fake-jwt-token", token);
        verify(jwtService, times(1)).generateToken(existingUser);
    }

    @Test
    void login_ShouldThrowException_WhenEmailNotFound() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.empty());

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> userService.login(loginRequest));

        assertEquals("Invalid Email", exception.getMessage());
        // JWT should never be generated if the user doesn't exist
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void login_ShouldThrowException_WhenPasswordIsIncorrect() {
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(false);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> userService.login(loginRequest));

        assertEquals("Invalid Password", exception.getMessage());
        verify(jwtService, never()).generateToken(any(User.class));
    }
}