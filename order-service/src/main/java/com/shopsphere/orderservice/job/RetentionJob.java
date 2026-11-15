package com.shopsphere.orderservice.job;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopsphere.orderservice.config.OutboxProperties;
import com.shopsphere.orderservice.repository.InboxEventRepository;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

/**
 * Keeps the messaging tables small: deletes PUBLISHED outbox rows and processed inbox rows older than the
 * retention. DEAD outbox rows are never deleted automatically (they need a human). Idempotent, so safe on
 * several replicas.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final OutboxEventRepository outbox;
    private final InboxEventRepository inbox;
    private final OutboxProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RetentionJob(OutboxEventRepository outbox, InboxEventRepository inbox, OutboxProperties properties,
                        TransactionTemplate transaction, Clock clock) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Scheduled(cron = "${outbox.cleanup.cron}")
    public void purge() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(properties.cleanup().retentionDays());
            Integer published = transaction.execute(status -> outbox.deletePublishedBefore(cutoff));
            Integer processed = transaction.execute(status -> inbox.deleteProcessedBefore(cutoff));
            log.info("Retention job done: outboxPublished={} inboxProcessed={}", published, processed);
        } catch (RuntimeException e) {
            log.error("Retention job failed", e);
        }
    }
}
