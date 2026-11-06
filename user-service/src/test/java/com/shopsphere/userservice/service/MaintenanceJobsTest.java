
package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.shopsphere.userservice.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceJobsTest {

    private RefreshTokenService refreshTokens;
    private UserTokenService userTokens;
    private AuditService audit;
    private AppProperties props;

    private MaintenanceJobs maintenanceJobs;

    @BeforeEach
    void setUp() {
        refreshTokens = mock(RefreshTokenService.class);
        userTokens = mock(UserTokenService.class);
        audit = mock(AuditService.class);
        props = mock(AppProperties.class, RETURNS_DEEP_STUBS);

        when(props.audit().retentionDays()).thenReturn(30);

        maintenanceJobs = new MaintenanceJobs(
                refreshTokens,
                userTokens,
                audit,
                props
        );
    }

    @Test
    void purge_shouldExecuteAllCleanupOperations() {
        when(refreshTokens.purgeExpired()).thenReturn(2);
        when(userTokens.purgeExpired()).thenReturn(3);
        when(audit.purgeOlderThanDays(30)).thenReturn(4);

        assertDoesNotThrow(() -> maintenanceJobs.purge());

        verify(refreshTokens).purgeExpired();
        verify(userTokens).purgeExpired();
        verify(audit).purgeOlderThanDays(30);
    }

    @Test
    void purge_shouldHandleRuntimeException() {
        when(refreshTokens.purgeExpired())
                .thenThrow(new RuntimeException("Database unavailable"));

        assertDoesNotThrow(() -> maintenanceJobs.purge());

        verify(refreshTokens).purgeExpired();
        verifyNoInteractions(userTokens, audit);
    }
}
