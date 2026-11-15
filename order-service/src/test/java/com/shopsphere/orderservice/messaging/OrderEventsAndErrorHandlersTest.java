package com.shopsphere.orderservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.messaging.outbox.OutboxWriter;

class OrderEventsAndErrorHandlersTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);

    private static OrderProperties props(boolean legacyEnabled) {
        return new OrderProperties(
                "INR", 5, 100, false,
                new OrderProperties.Events("order.events.v1", legacyEnabled, "order-events"),
                new OrderProperties.Payment(false),
                new OrderProperties.Expiry(false, Duration.ofMinutes(15), Duration.ofMinutes(30), 60000),
                new OrderProperties.Inbound(false, "inventory.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(2, Duration.ofMillis(10), 2.0, Duration.ofMillis(100))),
                new OrderProperties.Catalog(3, Duration.ofMillis(200), 10, 50, Duration.ofSeconds(10))
        );
    }

    @Test
    void publish_enqueuesEnvelopeAndLegacyCommandForSingleItemOrders() {
        OutboxWriter outbox = mock(OutboxWriter.class);
        OrderEvents publisher = new OrderEvents(outbox, mapper, props(true), clock);

        Order order = new Order();
        order.setId(77L);
        order.setUserId(9L);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency("INR");
        order.setTotalPrice(new BigDecimal("199.00"));
        order.setIdempotencyKey("idem-77");
        order.setOrderDate(LocalDateTime.of(2026, 7, 1, 0, 0));
        order.setShippingAddress(new Address("Bob", "Road 1", null, "Delhi", "DL", "110001", "IN", null));
        order.addItem(new OrderItem(11L, "Book", new BigDecimal("199.00"), 1));

        MDC.put("requestId", "corr-12345678");
        try {
            publisher.publish(order, EventTypes.ORDER_PLACED, null);
        } finally {
            MDC.remove("requestId");
        }

        verify(outbox).enqueue(eq("order.events.v1"), eq("77"), eq(EventTypes.ORDER_PLACED), any(), eq(77L), any());
        ArgumentCaptor<String> legacyPayload = ArgumentCaptor.forClass(String.class);
        verify(outbox).enqueue(eq("order-events"), eq("77"), eq(EventTypes.LEGACY_ORDER_CREATED), any(), eq(77L), legacyPayload.capture());
        assertThat(legacyPayload.getValue()).contains("\"eventType\":\"ORDER_CREATED\"");

        order.setStatus(OrderStatus.CANCEL_REQUESTED);
        order.setCancellationCommandId("cmd-abc");
        publisher.publish(order, EventTypes.ORDER_CANCEL_REQUESTED, "user cancelled");
        verify(outbox, times(4)).enqueue(any(), eq("77"), any(), any(), eq(77L), any());

        publisher.publish(order, EventTypes.ORDER_CONFIRMED, "reserved");
        verify(outbox, times(5)).enqueue(any(), eq("77"), any(), any(), eq(77L), any());
    }

    @Test
    void publish_skipsLegacyWhenDisabledOrMultiItem() {
        OutboxWriter outbox = mock(OutboxWriter.class);
        OrderEvents publisher = new OrderEvents(outbox, mapper, props(false), clock);

        Order order = new Order();
        order.setId(88L);
        order.setUserId(1L);
        order.setStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.of(2026, 7, 1, 0, 0));
        order.addItem(new OrderItem(1L, "A", BigDecimal.TEN, 1));

        publisher.publish(order, EventTypes.ORDER_PLACED, null);
        verify(outbox, never()).enqueue(eq("order-events"), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaErrorHandlers_createsConfiguredHandler() {
        KafkaOperations<String, String> template = mock(KafkaOperations.class);
        AtomicBoolean deadLettered = new AtomicBoolean(false);
        DefaultErrorHandler handler = KafkaErrorHandlers.create(
                template,
                new OrderProperties.Inbound.Retry(1, Duration.ofMillis(5), 2.0, Duration.ofMillis(20)),
                rec -> deadLettered.set(true));

        assertThat(handler).isNotNull();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory.events.v1", 0, 0L, "1", "{}");
        assertThat(record.topic()).isEqualTo("inventory.events.v1");
    }
}
