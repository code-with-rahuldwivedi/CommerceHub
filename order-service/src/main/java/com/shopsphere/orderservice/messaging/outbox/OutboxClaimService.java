package com.shopsphere.orderservice.messaging.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.config.OutboxProperties;
import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

/** Short transaction: lock a batch (SKIP LOCKED), mark it PROCESSING, commit. Kafka is called AFTER this commits. */
@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxClaimService(OutboxEventRepository repository, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimPendingEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        OutboxProperties.Publisher publisher = properties.publisher();

        List<OutboxEvent> events = repository.claimPendingEvents(
                now, now.minus(publisher.staleClaim()), publisher.batchSize());
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEvent.PROCESSING);
            event.setClaimedAt(now);
        }
        return events.stream().map(ClaimedOutboxEvent::from).toList();
    }
}
