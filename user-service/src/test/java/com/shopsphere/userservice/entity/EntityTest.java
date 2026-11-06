package com.shopsphere.userservice.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class EntityTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 6, 10, 0);

    // ---------------- AUDIT EVENT ----------------

    @Test
    void auditEventConstructorAndGetters() {
        AuditEvent event = new AuditEvent(
                now, "USER_LOGIN", 10L, 20L, "Successful login");

        assertNull(event.getId());
        assertEquals(now, event.getOccurredAt());
        assertEquals("USER_LOGIN", event.getAction());
        assertEquals(10L, event.getActorUserId());
        assertEquals(20L, event.getTargetUserId());
        assertEquals("Successful login", event.getDetails());
    }

    @Test
    void auditEventNoArgsConstructor() {
        AuditEvent event = new AuditEvent();

        assertNull(event.getId());
        assertNull(event.getOccurredAt());
        assertNull(event.getAction());
        assertNull(event.getActorUserId());
        assertNull(event.getTargetUserId());
        assertNull(event.getDetails());
    }

    // ---------------- USER TOKEN ----------------

    @Test
    void userTokenConstructorAndGetters() {
        LocalDateTime expiry = now.plusHours(1);

        UserToken token = new UserToken(
                10L, TokenType.EMAIL_VERIFICATION,
                "sha256-hash", expiry, now);

        assertNull(token.getId());
        assertEquals(10L, token.getUserId());
        assertEquals(TokenType.EMAIL_VERIFICATION, token.getType());
        assertEquals("sha256-hash", token.getTokenHash());
        assertEquals(expiry, token.getExpiresAt());
        assertNull(token.getUsedAt());
        assertEquals(now, token.getCreatedAt());
    }

    @Test
    void userTokenNoArgsConstructor() {
        UserToken token = new UserToken();

        assertNull(token.getId());
        assertNull(token.getUserId());
        assertNull(token.getType());
        assertNull(token.getTokenHash());
        assertNull(token.getExpiresAt());
        assertNull(token.getUsedAt());
        assertNull(token.getCreatedAt());
    }

    // ---------------- REFRESH TOKEN ----------------

    @Test
    void refreshTokenConstructorAndGetters() {
        LocalDateTime expiry = now.plusDays(7);

        RefreshToken token = new RefreshToken(
                15L, "refresh-hash", "family-123", expiry, now);

        assertNull(token.getId());
        assertEquals(15L, token.getUserId());
        assertEquals("refresh-hash", token.getTokenHash());
        assertEquals("family-123", token.getFamilyId());
        assertEquals(expiry, token.getExpiresAt());
        assertNull(token.getRevokedAt());
        assertEquals(now, token.getCreatedAt());
    }

    @Test
    void refreshTokenNoArgsConstructor() {
        RefreshToken token = new RefreshToken();

        assertNull(token.getId());
        assertNull(token.getUserId());
        assertNull(token.getTokenHash());
        assertNull(token.getFamilyId());
        assertNull(token.getExpiresAt());
        assertNull(token.getRevokedAt());
        assertNull(token.getCreatedAt());
    }

    // ---------------- USER ----------------

    @Test
    void userDefaultValuesAndGetters() {
        User user = new User();

        assertNull(user.getId());
        assertNull(user.getName());
        assertNull(user.getEmail());
        assertNull(user.getPassword());
        assertEquals(Role.USER, user.getRole());
        assertTrue(user.isEnabled());
        assertFalse(user.isEmailVerified());
        assertNull(user.getPasswordChangedAt());
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockedUntil());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @Test
    void userSettersAndGetters() {
        User user = new User();
        LocalDateTime passwordChanged = now.minusDays(1);
        LocalDateTime lockedUntil = now.plusMinutes(15);

        user.setId(1L);
        user.setName("Rahul");
        user.setEmail("rahul@example.com");
        user.setPassword("encoded-password");
        user.setRole(Role.ADMIN);
        user.setEnabled(false);
        user.setEmailVerified(true);
        user.setPasswordChangedAt(passwordChanged);
        user.setFailedAttempts(3);
        user.setLockedUntil(lockedUntil);

        assertEquals(1L, user.getId());
        assertEquals("Rahul", user.getName());
        assertEquals("rahul@example.com", user.getEmail());
        assertEquals("encoded-password", user.getPassword());
        assertEquals(Role.ADMIN, user.getRole());
        assertFalse(user.isEnabled());
        assertTrue(user.isEmailVerified());
        assertEquals(passwordChanged, user.getPasswordChangedAt());
        assertEquals(3, user.getFailedAttempts());
        assertEquals(lockedUntil, user.getLockedUntil());
    }

    @Test
    void isLockedAtReturnsFalseWhenLockedUntilIsNull() {
        User user = new User();

        assertFalse(user.isLockedAt(now));
    }

    @Test
    void isLockedAtReturnsTrueWhenLockIsInFuture() {
        User user = new User();
        user.setLockedUntil(now.plusMinutes(10));

        assertTrue(user.isLockedAt(now));
    }

    @Test
    void isLockedAtReturnsFalseWhenLockHasExpired() {
        User user = new User();
        user.setLockedUntil(now.minusMinutes(10));

        assertFalse(user.isLockedAt(now));
    }

    @Test
    void isLockedAtReturnsFalseWhenLockExpiresExactlyNow() {
        User user = new User();
        user.setLockedUntil(now);

        assertFalse(user.isLockedAt(now));
    }

    @Test
    void onCreateSetsCreatedAtAndUpdatedAt() {
        User user = new User();

        user.onCreate();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test
    void onUpdateChangesUpdatedAt() throws InterruptedException {
        User user = new User();
        user.onCreate();

        LocalDateTime previousUpdatedAt = user.getUpdatedAt();

        Thread.sleep(2);
        user.onUpdate();

        assertNotNull(user.getUpdatedAt());
        assertTrue(user.getUpdatedAt().isAfter(previousUpdatedAt));
    }
}
