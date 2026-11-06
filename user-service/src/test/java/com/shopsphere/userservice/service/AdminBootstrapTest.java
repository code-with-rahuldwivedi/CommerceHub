
package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService audit;

    @Mock
    private ApplicationArguments args;

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "StrongPassword123";

    private AdminBootstrap createService(String email, String password, String name) {

        AppProperties properties = new AppProperties(
                new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppProperties.RateLimit(true, 100),
                new AppProperties.Bootstrap(email, password, name),
                new AppProperties.Account(
                        true,
                        Duration.ofHours(24),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(5),
                        "http://localhost/verify?token={token}",
                        "http://localhost/reset?token={token}"
                ),
                new AppProperties.Mail("noreply@example.com", false),
                new AppProperties.Audit(90),
                new AppProperties.Request(1048576)
        );

        return new AdminBootstrap(users, passwordEncoder, properties, audit);
    }

    private AdminBootstrap service() {
        return createService(EMAIL, PASSWORD, "Rahul");
    }

    @Test
    void run_whenEmailIsNull_skipsBootstrap() {

        createService(null, PASSWORD, "Admin").run(args);

        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void run_whenEmailIsBlank_skipsBootstrap() {

        createService("   ", PASSWORD, "Admin").run(args);

        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void run_whenPasswordIsNull_skipsBootstrap() {

        createService(EMAIL, null, "Admin").run(args);

        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void run_whenPasswordIsBlank_skipsBootstrap() {

        createService(EMAIL, "   ", "Admin").run(args);

        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void run_whenPasswordIsTooShort_throwsException() {

        AdminBootstrap bootstrap = createService(EMAIL, "short", "Admin");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrap.run(args)
        );

        assertEquals(
                "app.bootstrap.admin-password must be at least 12 characters",
                exception.getMessage()
        );

        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void run_whenAdminAlreadyExists_skipsCreation() {

        when(users.existsByEmail(EMAIL)).thenReturn(true);

        service().run(args);

        verify(users).existsByEmail(EMAIL);
        verify(users, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, audit);
    }

    @Test
    void run_whenAdminDoesNotExist_createsAdminSuccessfully() {

        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        service().run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());

        User savedAdmin = captor.getValue();

        assertEquals("Rahul", savedAdmin.getName());
        assertEquals(EMAIL, savedAdmin.getEmail());
        assertEquals("encoded-password", savedAdmin.getPassword());
        assertEquals(Role.ADMIN, savedAdmin.getRole());
        assertEquals(true, savedAdmin.isEmailVerified());

        verify(passwordEncoder).encode(PASSWORD);

        verify(audit).record(
                eq(AuditAction.ADMIN_BOOTSTRAPPED),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    void run_whenAdminNameIsBlank_usesDefaultName() {

        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        createService(EMAIL, PASSWORD, "   ").run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());

        assertEquals("Administrator", captor.getValue().getName());
    }

    @Test
    void run_whenAdminNameIsNull_usesDefaultName() {

        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        createService(EMAIL, PASSWORD, null).run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());

        assertEquals("Administrator", captor.getValue().getName());
    }

    @Test
    void run_whenAdminNameHasSpaces_trimsName() {

        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        createService(EMAIL, PASSWORD, "  Rahul  ").run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());

        assertEquals("Rahul", captor.getValue().getName());
    }

    @Test
    void run_whenAnotherInstanceCreatesAdmin_handlesDuplicateGracefully() {

        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(users.existsByEmail(EMAIL)).thenReturn(false);
        when(users.save(any(User.class))).thenThrow(
                new DataIntegrityViolationException("Duplicate email")
        );

        assertDoesNotThrow(() -> service().run(args));

        verify(users).save(any(User.class));
        verifyNoInteractions(audit);
    }
}
