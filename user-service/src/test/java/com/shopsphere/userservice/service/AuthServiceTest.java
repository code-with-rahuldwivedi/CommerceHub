package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopsphere.userservice.TestProps;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.dto.TokenResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.EmailAlreadyExistsException;
import com.shopsphere.userservice.exception.EmailNotVerifiedException;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.exception.InvalidRequestException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.AppUserDetails;
import com.shopsphere.userservice.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicy passwordPolicy;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private LoginAttemptService loginAttempts;
    @Mock private AccountService accountService;
    @Mock private AuthMetrics metrics;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = newService(false);
    }

    private AuthService newService(boolean requireVerification) {
        return new AuthService(users, passwordEncoder, passwordPolicy, authenticationManager, jwtService,
                refreshTokens, loginAttempts, accountService, metrics, TestProps.app(requireVerification),
                TestProps.CLOCK);
    }

    private static AppUserDetails principal(int failedAttempts, boolean verified) {
        return new AppUserDetails(1L, "a@b.com", "HASH", Role.USER, true, false, failedAttempts, verified);
    }

    private void authenticationSucceedsWith(AppUserDetails principal) {
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static User user(long id, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setEmail("a@b.com");
        user.setName("A");
        user.setRole(Role.USER);
        user.setEnabled(enabled);
        return user;
    }

    // ---------- register ----------

    @Test
    void register_normalizesEmail_hashesPassword_forcesUserRole_andSendsVerification() {
        when(users.existsByEmail("rahul@example.com")).thenReturn(false);
        when(passwordEncoder.encode("S3cure-pass!")).thenReturn("HASH");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("  Rahul  ", "  Rahul@Example.COM ", "S3cure-pass!"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("rahul@example.com", saved.getValue().getEmail());
        assertEquals("Rahul", saved.getValue().getName());
        assertEquals("HASH", saved.getValue().getPassword());
        assertEquals(Role.USER, saved.getValue().getRole());
        verify(passwordPolicy).validate("S3cure-pass!", "rahul@example.com");
        verify(accountService).sendVerificationEmail(saved.getValue());
        verify(metrics).register();
    }

    @Test
    void register_existingEmail_isRejectedBeforeHashing() {
        when(users.existsByEmail("rahul@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register(new RegisterRequest("Rahul", "rahul@example.com", "S3cure-pass!")));

        verify(passwordEncoder, never()).encode(anyString());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrentDuplicate_isMappedToConflict() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register(new RegisterRequest("Rahul", "rahul@example.com", "S3cure-pass!")));

        verify(accountService, never()).sendVerificationEmail(any());
    }

    @Test
    void register_weakPassword_isRejectedByPolicy_andNothingIsSaved() {
        doThrow(new InvalidRequestException("too common"))
                .when(passwordPolicy).validate(anyString(), anyString());

        assertThrows(InvalidRequestException.class,
                () -> service.register(new RegisterRequest("Rahul", "rahul@example.com", "password123")));

        verify(users, never()).saveAndFlush(any());
    }

    // ---------- login ----------

    @Test
    void login_success_returnsTokens_andNormalizesEmail() {
        authenticationSucceedsWith(principal(0, true));
        when(jwtService.generateAccessToken(1L, Role.USER)).thenReturn("access");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);
        when(refreshTokens.startSession(1L)).thenReturn("refresh");

        TokenResponse response = service.login(new LoginRequest("  A@B.com ", "pw"));

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());

        ArgumentCaptor<Authentication> auth = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(auth.capture());
        assertEquals("a@b.com", auth.getValue().getName());
        verify(loginAttempts, never()).reset(anyLong());
        verify(metrics).login("success");
    }

    @Test
    void login_success_resetsPreviousFailedAttempts() {
        authenticationSucceedsWith(principal(2, true));
        when(jwtService.generateAccessToken(1L, Role.USER)).thenReturn("access");
        when(refreshTokens.startSession(1L)).thenReturn("refresh");

        service.login(new LoginRequest("a@b.com", "pw"));

        verify(loginAttempts).reset(1L);
    }

    @Test
    void login_badCredentials_countsFailure_andThrowsGenericException() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("A@B.com", "wrong")));

        verify(loginAttempts).recordFailure("a@b.com");
        verify(metrics).login("bad_credentials");
        verify(jwtService, never()).generateAccessToken(anyLong(), any());
    }

    @Test
    void login_lockedAccount_doesNotCountFailure_andLooksLikeBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("a@b.com", "pw")));

        verify(loginAttempts, never()).recordFailure(anyString());
        verify(metrics).login("rejected");
    }

    @Test
    void login_unverifiedEmail_isBlockedOnlyAfterCorrectPassword_whenVerificationIsRequired() {
        AuthService strict = newService(true);
        authenticationSucceedsWith(principal(0, false));

        assertThrows(EmailNotVerifiedException.class, () -> strict.login(new LoginRequest("a@b.com", "pw")));

        verify(metrics).login("unverified");
        verify(refreshTokens, never()).startSession(anyLong());
    }

    @Test
    void login_unverifiedEmail_isAllowedWhenVerificationIsNotRequired() {
        authenticationSucceedsWith(principal(0, false));
        when(jwtService.generateAccessToken(1L, Role.USER)).thenReturn("access");
        when(refreshTokens.startSession(1L)).thenReturn("refresh");

        assertEquals("access", service.login(new LoginRequest("a@b.com", "pw")).accessToken());
    }

    // ---------- refresh ----------

    @Test
    void refresh_success_rereadsUser_andIssuesNewPair() {
        when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(1L, "fam", "newRefresh"));
        User user = user(1L, true);
        user.setRole(Role.ADMIN); // role changed since the last login: refresh must pick it up
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(1L, Role.ADMIN)).thenReturn("access");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);

        TokenResponse response = service.refresh("old");

        assertEquals("access", response.accessToken());
        assertEquals("newRefresh", response.refreshToken());
        verify(metrics).refresh("success");
    }

    @Test
    void refresh_disabledUser_revokesSession_andIsRejected() {
        when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(1L, "fam", "newRefresh"));
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, false)));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("old"));

        verify(refreshTokens).revokeFamily("fam");
        verify(metrics).refresh("failure");
        verify(jwtService, never()).generateAccessToken(anyLong(), any());
    }

    @Test
    void refresh_lockedUser_isRejected() {
        when(refreshTokens.rotate("old")).thenReturn(new RefreshTokenService.Rotation(1L, "fam", "newRefresh"));
        User locked = user(1L, true);
        locked.setLockedUntil(LocalDateTime.now(TestProps.CLOCK).plusMinutes(5));
        when(users.findById(1L)).thenReturn(Optional.of(locked));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("old"));

        verify(refreshTokens).revokeFamily("fam");
    }

    @Test
    void refresh_invalidToken_countsFailureMetric() {
        when(refreshTokens.rotate("bad")).thenThrow(new InvalidRefreshTokenException());

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("bad"));

        verify(metrics).refresh("failure");
    }
    
    @Test
    void appUserDetails_getUsername_returnsEmail() {
        AppUserDetails principal = principal(0, true);

        assertEquals("a@b.com", principal.getUsername());
    }

    @Test
    void logout_endsTheSession() {
        service.logout("tok");

        verify(refreshTokens).endSession("tok");
    }
}
