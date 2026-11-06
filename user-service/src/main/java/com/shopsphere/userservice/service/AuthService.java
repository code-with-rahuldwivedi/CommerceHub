package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;
import com.shopsphere.userservice.dto.TokenResponse;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.EmailAlreadyExistsException;
import com.shopsphere.userservice.exception.EmailNotVerifiedException;
import com.shopsphere.userservice.exception.InvalidCredentialsException;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.security.AppUserDetails;
import com.shopsphere.userservice.security.JwtService;
import com.shopsphere.userservice.util.Emails;
import com.shopsphere.userservice.util.LogSanitizer;

@Service
public class AuthService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final AccountService accountService;
    private final AuthMetrics metrics;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokens,
                       LoginAttemptService loginAttempts,
                       AccountService accountService,
                       AuthMetrics metrics,
                       AppProperties props,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.accountService = accountService;
        this.metrics = metrics;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        passwordPolicy.validate(request.password(), email);

        if (users.existsByEmail(email)) { // cheap fast path; the unique constraint below is the real guard
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER); // public registration can never create privileged accounts

        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException(); // lost a concurrent-registration race
        }

        accountService.sendVerificationEmail(user);
        metrics.register();
        AUDIT.info("user_registered userId={}", user.getId());
        return UserResponse.from(user);
    }

    /**
     * Deliberately NOT @Transactional: failed-attempt counters are committed by
     * LoginAttemptService in their own transactions even though this method throws.
     */
    public TokenResponse login(LoginRequest request) {
        String email = Emails.normalize(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException e) {
            loginAttempts.recordFailure(email);
            metrics.login("bad_credentials");
            AUDIT.warn("login_failed email={}", LogSanitizer.maskEmail(email));
            throw new InvalidCredentialsException();
        } catch (AuthenticationException e) {
            // locked / disabled: same response as bad credentials so accounts can't be probed
            metrics.login("rejected");
            AUDIT.warn("login_rejected email={} reason={}", LogSanitizer.maskEmail(email),
                    e.getClass().getSimpleName());
            throw new InvalidCredentialsException();
        }

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        if (principal.getFailedAttempts() > 0) {
            loginAttempts.reset(principal.getId());
        }

        // Only revealed AFTER a correct password, so it does not expose which e-mails exist.
        if (props.account().requireEmailVerification() && !principal.isEmailVerified()) {
            metrics.login("unverified");
            AUDIT.info("login_unverified userId={}", principal.getId());
            throw new EmailNotVerifiedException();
        }

        metrics.login("success");
        AUDIT.info("login_success userId={}", principal.getId());
        return new TokenResponse(
                jwtService.generateAccessToken(principal.getId(), principal.getRole()),
                refreshTokens.startSession(principal.getId()),
                TOKEN_TYPE,
                jwtService.accessTtlSeconds());
    }

    /** noRollbackFor keeps the family revocation committed when we reject the refresh. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        try {
            RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);

            // Re-read the user so role changes / disabling / locking apply at refresh time.
            User user = users.findById(rotation.userId()).orElse(null);
            if (user == null || !user.isEnabled() || user.isLockedAt(LocalDateTime.now(clock))) {
                refreshTokens.revokeFamily(rotation.familyId());
                throw new InvalidRefreshTokenException();
            }

            metrics.refresh("success");
            return new TokenResponse(
                    jwtService.generateAccessToken(user.getId(), user.getRole()),
                    rotation.refreshToken(),
                    TOKEN_TYPE,
                    jwtService.accessTtlSeconds());
        } catch (InvalidRefreshTokenException e) {
            metrics.refresh("failure");
            throw e;
        }
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.endSession(rawRefreshToken);
    }
}
