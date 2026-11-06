package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.entity.TokenType;
import com.shopsphere.userservice.entity.UserToken;
import com.shopsphere.userservice.exception.InvalidTokenException;
import com.shopsphere.userservice.repository.UserTokenRepository;
import com.shopsphere.userservice.util.TokenHasher;

/** Single-use, expiring tokens for e-mail verification and password reset. */
@Service
public class UserTokenService {

    private final UserTokenRepository repository;
    private final Clock clock;

    public UserTokenService(UserTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Invalidates the user's outstanding tokens of that type, then issues a new one (raw value returned once). */
    @Transactional
    public String issue(long userId, TokenType type, Duration ttl) {
        LocalDateTime now = now();
        repository.invalidateOutstanding(userId, type, now);

        String raw = TokenHasher.newRawToken();
        repository.save(new UserToken(userId, type, TokenHasher.hash(raw), now.plus(ttl), now));
        return raw;
    }

    /** True if a token of that type was issued within the cooldown (prevents e-mail bombing). */
    @Transactional(readOnly = true)
    public boolean recentlyIssued(long userId, TokenType type, Duration within) {
        return repository.existsByUserIdAndTypeAndCreatedAtAfter(userId, type, now().minus(within));
    }

    /** Validates without consuming. Throws if unknown, wrong type, used or expired. */
    @Transactional(readOnly = true)
    public UserToken find(String rawToken, TokenType type) {
        UserToken token = repository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidTokenException::new);
        if (token.getType() != type || token.getUsedAt() != null || token.getExpiresAt().isBefore(now())) {
            throw new InvalidTokenException();
        }
        return token;
    }

    /** Atomically marks the token used. Throws if someone else used it first. */
    @Transactional
    public void markUsed(UserToken token) {
        if (repository.markUsed(token.getId(), now()) == 0) {
            throw new InvalidTokenException();
        }
    }

    @Transactional
    public long consume(String rawToken, TokenType type) {
        UserToken token = find(rawToken, type);
        markUsed(token);
        return token.getUserId();
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
