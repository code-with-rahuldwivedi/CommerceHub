package com.shopsphere.orderservice.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import com.shopsphere.orderservice.config.OutboxProperties;
import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

class OutboxPipelineTest {

    private OutboxEventRepository repository;
    private OrderMetrics metrics;
    private OutboxProperties properties;
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        metrics = mock(OrderMetrics.class);
        properties = new OutboxProperties(
                new OutboxProperties.Publisher(true, 500, 50, Duration.ofMinutes(2), Duration.ofSeconds(5), 3),
                new OutboxProperties.Cleanup(true, "0 0 3 * * *", 7)
        );
    }

    @Test
    void outboxWriter_enqueuesPendingRowWithMdcCorrelationId() {
        OutboxWriter writer = new OutboxWriter(repository);
        MDC.put("requestId", "req-abcdef12");
        try {
            writer.enqueue("order.events.v1", "10", "ORDER_PLACED", "evt-1", 10L, "{\"a\":1}");
        } finally {
            MDC.remove("requestId");
        }

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.PENDING);
        assertThat(saved.getCorrelationId()).isEqualTo("req-abcdef12");
        assertThat(saved.getAggregateId()).isEqualTo(10L);
    }

    @Test
    void outboxClaimService_claimsEventsAndMarksProcessing() {
        OutboxEvent row = new OutboxEvent();
        ReflectionTestUtils.setField(row, "id", 1L);
        row.setAggregateId(10L);
        row.setTopic("order.events.v1");
        row.setEventType("ORDER_PLACED");
        row.setEventId("evt-1");
        row.setPayload("{}");

        LocalDateTime now = LocalDateTime.now(clock);
        when(repository.claimPendingEvents(now, now.minus(Duration.ofMinutes(2)), 50)).thenReturn(List.of(row));

        OutboxClaimService claimService = new OutboxClaimService(repository, properties, clock);
        List<ClaimedOutboxEvent> claimed = claimService.claimPendingEvents();

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).messageKey()).isEqualTo("10");
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.PROCESSING);
        assertThat(row.getClaimedAt()).isEqualTo(now);
    }

    @Test
    void outboxDeliveryService_marksPublishedRetriesWithBackoffAndParksDead() {
        OutboxDeliveryService delivery = new OutboxDeliveryService(repository, properties, metrics, clock);

        OutboxEvent event = new OutboxEvent();
        ReflectionTestUtils.setField(event, "id", 5L);
        event.setTopic("order.events.v1");
        event.setAttempts(0);
        when(repository.findById(5L)).thenReturn(Optional.of(event));

        delivery.markFailed(5L, "Timeout");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(LocalDateTime.now(clock).plusSeconds(2));
        verify(metrics).outboxFailed("order.events.v1");

        delivery.markFailed(5L, "Timeout 2");
        assertThat(event.getNextAttemptAt()).isEqualTo(LocalDateTime.now(clock).plusSeconds(4));

        delivery.markFailed(5L, "Timeout 3");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.DEAD);
        verify(metrics).outboxDead("order.events.v1");

        assertThat(OutboxDeliveryService.backoff(20)).isEqualTo(Duration.ofMinutes(5));

        delivery.markPublished(5L);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.PUBLISHED);
        assertThat(event.getLastError()).isNull();
        verify(metrics).outboxPublished("order.events.v1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void outboxPublisher_sendsRecordsWithHeadersAndHandlesFailures() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxDeliveryService deliveryService = mock(OutboxDeliveryService.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

        ClaimedOutboxEvent okEvent = new ClaimedOutboxEvent(1L, 10L, "order.events.v1", "10", "evt-1",
                "ORDER_PLACED", "corr-1", "{\"ok\":true}", 0);
        ClaimedOutboxEvent failEvent = new ClaimedOutboxEvent(2L, 11L, "order.events.v1", "11", "evt-2",
                "ORDER_FAILED", null, "{\"ok\":false}", 0);

        when(claimService.claimPendingEvents()).thenReturn(List.of(okEvent, failEvent));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Broker down")));

        OutboxPublisher publisher = new OutboxPublisher(claimService, deliveryService, kafka, properties);
        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, org.mockito.Mockito.times(2)).send(recordCaptor.capture());
        ProducerRecord<String, String> first = recordCaptor.getAllValues().get(0);
        assertThat(new String(first.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8)).isEqualTo("evt-1");
        assertThat(new String(first.headers().lastHeader("correlation-id").value(), StandardCharsets.UTF_8)).isEqualTo("corr-1");

        verify(deliveryService).markPublished(1L);
        verify(deliveryService).markFailed(eq(2L), any());
    }
}
