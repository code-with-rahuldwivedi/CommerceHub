package com.shopsphere.orderservice.messaging.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.InboxEvent;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.messaging.EventTypes;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.InboxEventRepository;
import com.shopsphere.orderservice.service.OrderLifecycleService;

class InboundMessagingTest {

    private OrderLifecycleService lifecycle;
    private InboxEventRepository inbox;
    private OrderMetrics metrics;
    private InboundEventDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lifecycle = mock(OrderLifecycleService.class);
        inbox = mock(InboxEventRepository.class);
        metrics = mock(OrderMetrics.class);
        dispatcher = new InboundEventDispatcher(
                List.of(new InventoryEventHandler(lifecycle), new PaymentEventHandler(lifecycle)),
                mapper, inbox, metrics, clock);
    }

    @Test
    void dispatch_routesInventoryAndPaymentEventsAndDeduplicates() {
        String reservedMsg = "{\"eventId\":\"evt-100001\",\"eventType\":\"INVENTORY_RESERVED\",\"correlationId\":\"corr-12345678\",\"payload\":{\"orderId\":55}}";
        when(inbox.existsByEventIdAndConsumer("evt-100001", "inventory")).thenReturn(false).thenReturn(true);

        dispatcher.dispatch("inventory.events.v1", reservedMsg);

        verify(lifecycle).onInventoryReserved(55L);
        verify(inbox).save(any(InboxEvent.class));
        verify(metrics).inbound("inventory", "processed");
        assertThat(MDC.get("requestId")).isNull();

        dispatcher.dispatch("inventory.events.v1", reservedMsg);
        verify(metrics).inbound("inventory", "duplicate");

        dispatcher.dispatch("inventory.events.v1",
                "{\"eventId\":\"evt-100002\",\"eventType\":\"INVENTORY_RESERVATION_FAILED\",\"payload\":{\"orderId\":55,\"reason\":\"none\"}}");
        verify(lifecycle).onInventoryReservationFailed(55L, "none");

        dispatcher.dispatch("inventory.events.v1",
                "{\"eventId\":\"evt-100003\",\"eventType\":\"INVENTORY_RELEASED\",\"payload\":{\"orderId\":55,\"commandId\":\"c1\",\"reason\":\"ok\"}}");
        verify(lifecycle).onCancellationResult(55L, "c1", true, "ok");

        dispatcher.dispatch("inventory.events.v1",
                "{\"eventId\":\"evt-100004\",\"eventType\":\"INVENTORY_RELEASE_FAILED\",\"payload\":{\"orderId\":55,\"commandId\":\"c2\",\"reason\":\"err\"}}");
        verify(lifecycle).onCancellationResult(55L, "c2", false, "err");

        dispatcher.dispatch("payment.events.v1",
                "{\"eventId\":\"evt-200001\",\"eventType\":\"PAYMENT_SUCCEEDED\",\"payload\":{\"orderId\":55,\"paymentId\":\"p1\"}}");
        verify(lifecycle).onPaymentSucceeded(55L, "p1");

        dispatcher.dispatch("payment.events.v1",
                "{\"eventId\":\"evt-200002\",\"eventType\":\"PAYMENT_FAILED\",\"payload\":{\"orderId\":55,\"reason\":\"declined\"}}");
        verify(lifecycle).onPaymentFailed(55L, "declined");
    }

    @Test
    void dispatch_ignoresUnknownEventTypesAndRejectsMalformedMessages() {
        dispatcher.dispatch("other.topic", "{\"eventId\":\"evt-999999\",\"eventType\":\"SOME_FUTURE_EVENT\"}");
        verify(metrics).inbound("unrouted", "ignored");
        verify(inbox, never()).save(any());

        assertThatThrownBy(() -> dispatcher.dispatch("t", "")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> dispatcher.dispatch("t", "not-json")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> dispatcher.dispatch("t", "[]")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> dispatcher.dispatch("t", "{\"eventId\":\"\"}")).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void inboundListener_and_payloadValidation_workCorrectly() {
        InboundEventDispatcher mockDispatcher = mock(InboundEventDispatcher.class);
        InboundEventListener listener = new InboundEventListener(mockDispatcher);
        listener.onMessage(new ConsumerRecord<>("inventory.events.v1", 0, 1L, "55", "{}"));
        verify(mockDispatcher).dispatch("inventory.events.v1", "{}");

        InventoryEventHandler invHandler = new InventoryEventHandler(lifecycle);
        assertThatThrownBy(() -> invHandler.handle(EventTypes.INVENTORY_RELEASED,
                mapper.createObjectNode().put("orderId", 1L), new EventContext("e", null, null)))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> invHandler.handle(EventTypes.INVENTORY_RESERVED,
                mapper.createObjectNode().put("orderId", -5L), new EventContext("e", null, null)))
                .isInstanceOf(InvalidEventException.class);
    }
}
