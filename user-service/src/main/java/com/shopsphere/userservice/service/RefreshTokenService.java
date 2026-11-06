package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.config.JwtProperties;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.RefreshToken;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.repository.RefreshTokenRepository;
import com.shopsphere.userservice.util.TokenHasher;

/**
 * Opaque, single-use, rotating refresh tokens with replay detection.
 * Presenting an already-used token revokes the entire session (family).
 */
@Service
public class RefreshTokenService {

    public record Rotation(long userId, String familyId, String refreshToken) {
    }

    private final RefreshTokenRepository repository;
    private final JwtProperties props;
    private final Clock clock;
    private final AuditService audit;
    private final AuthMetrics metrics;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties props, Clock clock,
                               AuditService audit, AuthMetrics metrics) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
        this.metrics = metrics;
    }

    /** Starts a new session (new family) and returns the raw refresh token. */
    @Transactional
    public String startSession(long userId) {
        return issue(userId, UUID.randomUUID().toString());
    }

    /**
     * noRollbackFor: when we detect replay we revoke the family and then throw;
     * that revocation must be committed, not rolled back with the exception.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        LocalDateTime now = now();
        RefreshToken current = repository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.getRevokedAt() != null) {
            return replayDetected(current, now);
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }
        // Atomic compare-and-set: if two requests race with the same token, only one wins.
        if (repository.revokeIfActive(current.getId(), now) == 0) {
            return replayDetected(current, now);
        }

        String next = issue(current.getUserId(), current.getFamilyId());
        return new Rotation(current.getUserId(), current.getFamilyId(), next);
    }

    /** Logout: revoke the whole session. Silent if the token is unknown (idempotent). */
    @Transactional
    public void endSession(String rawToken) {
        repository.findByTokenHash(TokenHasher.hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), now()));
    }

    @Transactional
    public void revokeFamily(String familyId) {
        repository.revokeFamily(familyId, now());
    }

    /** Signs the user out everywhere. Returns the number of sessions' tokens revoked. */
    @Transactional
    public int revokeAllForUser(long userId) {
        return repository.revokeAllForUser(userId, now());
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(now());
    }

    private Rotation replayDetected(RefreshToken token, LocalDateTime now) {
        repository.revokeFamily(token.getFamilyId(), now);
        metrics.refreshReplay();
        audit.record(AuditAction.REFRESH_REPLAY_DETECTED, null, token.getUserId(), "family=" + token.getFamilyId());
        throw new InvalidRefreshTokenException();
    }

    private String issue(long userId, String familyId) {
        String raw = TokenHasher.newRawToken();
        LocalDateTime now = now();
        repository.save(new RefreshToken(userId, TokenHasher.hash(raw), familyId, now.plus(props.refreshTtl()), now));
        return raw;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
