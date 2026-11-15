package com.shopsphere.orderservice.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.config.OutboxProperties;
import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

/** Records the outcome of one publish attempt (each in its own short transaction). */
@Service
public class OutboxDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final OrderMetrics metrics;
    private final Clock clock;

    public OutboxDeliveryService(OutboxEventRepository repository, OutboxProperties properties,
                                 OrderMetrics metrics, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void markPublished(long id) {
        repository.findById(id).ifPresent(event -> {
            event.setStatus(OutboxEvent.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now(clock));
            event.setLastError(null);
            metrics.outboxPublished(event.getTopic());
        });
    }

    /** Retries with exponential backoff; after "max-attempts" the event is parked as DEAD (alert) and stops blocking. */
    @Transactional
    public void markFailed(long id, String error) {
        repository.findById(id).ifPresent(event -> {
            int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(error));
            event.setClaimedAt(null);

            if (attempts >= properties.publisher().maxAttempts()) {
                event.setStatus(OutboxEvent.DEAD);
                metrics.outboxDead(event.getTopic());
                log.error("ALERT outbox event {} ({} for order {}) parked as DEAD after {} attempts: {}",
                        id, event.getEventType(), event.getAggregateId(), attempts, error);
                return;
            }
            event.setStatus(OutboxEvent.PENDING);
            event.setNextAttemptAt(LocalDateTime.now(clock).plus(backoff(attempts)));
            metrics.outboxFailed(event.getTopic());
        });
    }

    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(Math.max(attempts, 1), 20); // 2s, 4s, 8s, ...
        Duration delay = Duration.ofSeconds(seconds);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
