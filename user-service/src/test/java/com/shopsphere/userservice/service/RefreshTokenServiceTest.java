package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopsphere.userservice.TestProps;
import com.shopsphere.userservice.config.JwtProperties;
import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.RefreshToken;
import com.shopsphere.userservice.exception.InvalidRefreshTokenException;
import com.shopsphere.userservice.repository.RefreshTokenRepository;
import com.shopsphere.userservice.util.TokenHasher;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(TestProps.CLOCK);

    @Mock private RefreshTokenRepository repository;
    @Mock private AuditService audit;
    @Mock private AuthMetrics metrics;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(null, List.of(), true, "iss", "aud",
                Duration.ofMinutes(15), Duration.ofDays(7));
        service = new RefreshTokenService(repository, props, TestProps.CLOCK, audit, metrics);
    }

    private RefreshToken storedToken() {
        RefreshToken token = mock(RefreshToken.class);
        when(repository.findByTokenHash(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));

        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void rotate_success_revokesOldToken_andIssuesNewOneInTheSameFamily() {
        RefreshToken token = storedToken();
        when(token.getId()).thenReturn(10L);
        when(token.getUserId()).thenReturn(5L);
        when(token.getFamilyId()).thenReturn("fam");
        when(token.getExpiresAt()).thenReturn(NOW.plusDays(1));
        when(repository.revokeIfActive(eq(10L), any())).thenReturn(1);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate("raw");

        assertEquals(5L, rotation.userId());
        assertEquals("fam", rotation.familyId());
        assertNotNull(rotation.refreshToken());
        assertNotEquals("raw", rotation.refreshToken());
        verify(repository).revokeIfActive(eq(10L), any());
    }

    @Test
    void rotate_alreadyRevokedToken_isReplay_revokesWholeFamily_andIsAudited() {
        RefreshToken token = storedToken();
        when(token.getRevokedAt()).thenReturn(NOW.minusMinutes(1));
        when(token.getFamilyId()).thenReturn("fam");
        when(token.getUserId()).thenReturn(5L);

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));

        verify(repository).revokeFamily(eq("fam"), any());
        verify(metrics).refreshReplay();
        verify(audit).record(eq(AuditAction.REFRESH_REPLAY_DETECTED), isNull(), eq(5L), anyString());
    }

    @Test
    void rotate_losingARace_isTreatedAsReplay() {
        RefreshToken token = storedToken();
        when(token.getId()).thenReturn(10L);
        when(token.getUserId()).thenReturn(5L);
        when(token.getFamilyId()).thenReturn("fam");
        when(token.getExpiresAt()).thenReturn(NOW.plusDays(1));
        when(repository.revokeIfActive(eq(10L), any())).thenReturn(0); // someone else rotated it first

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));

        verify(repository).revokeFamily(eq("fam"), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_expiredToken_isRejected_withoutTouchingTheFamily() {
        RefreshToken token = storedToken();
        when(token.getExpiresAt()).thenReturn(NOW.minusSeconds(1));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw"));

        verify(repository, never()).revokeFamily(anyString(), any());
        verify(repository, never()).revokeIfActive(any(), any());
    }

    @Test
    void endSession_revokesFamily_andIsSilentForUnknownTokens() {
        RefreshToken token = storedToken();
        when(token.getFamilyId()).thenReturn("fam");

        service.endSession("raw");
        verify(repository).revokeFamily(eq("fam"), any());

        when(repository.findByTokenHash(TokenHasher.hash("unknown"))).thenReturn(Optional.empty());
        service.endSession("unknown"); // must not throw
    }

    @Test
    void revokeAllForUser_delegatesToTheRepository() {
        when(repository.revokeAllForUser(eq(5L), any())).thenReturn(3);

        assertEquals(3, service.revokeAllForUser(5L));
    }
}
