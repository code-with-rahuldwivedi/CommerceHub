package com.shopsphere.userservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopsphere.userservice.config.AppProperties;

/**
 * Housekeeping. Every statement is an idempotent delete, so running on several replicas is safe;
 * set PURGE_CRON=- on all but one replica to avoid redundant work.
 */
@Component
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final RefreshTokenService refreshTokens;
    private final UserTokenService userTokens;
    private final AuditService audit;
    private final AppProperties props;

    public MaintenanceJobs(RefreshTokenService refreshTokens, UserTokenService userTokens,
                           AuditService audit, AppProperties props) {
        this.refreshTokens = refreshTokens;
        this.userTokens = userTokens;
        this.audit = audit;
        this.props = props;
    }

    @Scheduled(cron = "${app.jobs.purge-cron}")
    public void purge() {
        try {
            int refresh = refreshTokens.purgeExpired();
            int tokens = userTokens.purgeExpired();
            int events = audit.purgeOlderThanDays(props.audit().retentionDays());
            log.info("Housekeeping done: refreshTokens={} userTokens={} auditEvents={}", refresh, tokens, events);
        } catch (RuntimeException e) {
            log.error("Housekeeping failed", e);
        }
    }
}
