package com.shopsphere.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.OrderStatusHistory;
import com.shopsphere.orderservice.exception.InvalidOrderStateException;
import com.shopsphere.orderservice.exception.MissingOrderException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.messaging.EventTypes;
import com.shopsphere.orderservice.messaging.OrderEvents;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.OrderStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock private OrderRepository orders;
    @Mock private OrderStatusHistoryRepository history;
    @Mock private OrderEvents events;
    @Mock private OrderMetrics metrics;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private OrderLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new OrderLifecycleService(orders, history, events, props(false), metrics, clock);
    }

    private static OrderProperties props(boolean paymentEnabled) {
        return new OrderProperties(
                "INR", 5, 100, false,
                new OrderProperties.Events("order.events.v1", true, "order-events"),
                new OrderProperties.Payment(paymentEnabled),
                new OrderProperties.Expiry(true, Duration.ofMinutes(15), Duration.ofMinutes(30), 60000),
                new OrderProperties.Inbound(true, "inventory.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10))),
                new OrderProperties.Catalog(3, Duration.ofMillis(200), 10, 50, Duration.ofSeconds(10))
        );
    }

    private static Order order(long id, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(42L);
        o.setStatus(status);
        return o;
    }

    @Test
    void place_savesPendingOrderRecordsHistoryAndPublishesEvent() {
        Order order = order(10L, null);
        when(orders.saveAndFlush(order)).thenReturn(order);

        Order saved = service.place(order, "user:42");

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(history).save(any(OrderStatusHistory.class));
        verify(events).publish(order, EventTypes.ORDER_PLACED, null);
        verify(metrics).transition(null, OrderStatus.PENDING);
    }

    @Test
    void requestCancellation_handlesIdempotentAndValidStatesAndRejectsInvalid() {
        Order alreadyCancelled = order(1L, OrderStatus.CANCELLED);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(alreadyCancelled));
        assertThat(service.requestCancellation(1L, "reason", "user:42")).isSameAs(alreadyCancelled);
        verifyNoInteractions(events);

        Order confirmed = order(2L, OrderStatus.CONFIRMED);
        when(orders.findWithItemsById(2L)).thenReturn(Optional.of(confirmed));
        service.requestCancellation(2L, "  ", "user:42");
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(confirmed.getCancellationCommandId()).isNotBlank();
        assertThat(confirmed.getCancellationReason()).isEqualTo("Cancelled on request");
        verify(events).publish(confirmed, EventTypes.ORDER_CANCEL_REQUESTED, "Cancelled on request");

        Order shipped = order(3L, OrderStatus.SHIPPED);
        when(orders.findWithItemsById(3L)).thenReturn(Optional.of(shipped));
        assertThatThrownBy(() -> service.requestCancellation(3L, "late", "user:42"))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void markShippedAndDelivered_areIdempotentAndEnforceStateMachine() {
        Order confirmed = order(1L, OrderStatus.CONFIRMED);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(confirmed));

        service.markShipped(1L, "admin:1");
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(events).publish(confirmed, EventTypes.ORDER_SHIPPED, null);

        service.markShipped(1L, "admin:1");
        verify(events).publish(confirmed, EventTypes.ORDER_SHIPPED, null);

        service.markDelivered(1L, "admin:1");
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(events).publish(confirmed, EventTypes.ORDER_DELIVERED, null);

        service.markDelivered(1L, "admin:1");
        verify(events).publish(confirmed, EventTypes.ORDER_DELIVERED, null);

        Order pending = order(2L, OrderStatus.PENDING);
        when(orders.findWithItemsById(2L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.markDelivered(2L, "admin:1"))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void onInventoryReserved_transitionsAccordingToPaymentFlagAndIgnoresStale() {
        Order pending = order(1L, OrderStatus.PENDING);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(pending));

        service.onInventoryReserved(1L);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(events).publish(pending, EventTypes.ORDER_CONFIRMED, "Inventory reserved");

        service.onInventoryReserved(1L);
        verify(events).publish(pending, EventTypes.ORDER_CONFIRMED, "Inventory reserved");

        OrderLifecycleService paymentEnabledService =
                new OrderLifecycleService(orders, history, events, props(true), metrics, clock);
        Order pending2 = order(2L, OrderStatus.PENDING);
        when(orders.findWithItemsById(2L)).thenReturn(Optional.of(pending2));

        paymentEnabledService.onInventoryReserved(2L);
        assertThat(pending2.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(events).publish(pending2, EventTypes.ORDER_AWAITING_PAYMENT, "Inventory reserved");
    }

    @Test
    void onInventoryReservationFailed_transitionsToFailed() {
        Order pending = order(1L, OrderStatus.PENDING);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(pending));

        service.onInventoryReservationFailed(1L, "Out of stock");
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(events).publish(pending, EventTypes.ORDER_FAILED, "Out of stock");
    }

    @Test
    void onCancellationResult_matchesCommandIdAndIgnoresStaleCommands() {
        Order order = order(1L, OrderStatus.CANCEL_REQUESTED);
        order.setCancellationCommandId("cmd-current");
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(order));

        service.onCancellationResult(1L, "cmd-old", true, "released");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);

        service.onCancellationResult(1L, "cmd-current", false, "DB lock");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);
        verify(events).publish(order, EventTypes.ORDER_CANCEL_FAILED, "DB lock");

        order.setStatus(OrderStatus.CANCEL_REQUESTED);
        order.setCancellationCommandId("cmd-2");
        service.onCancellationResult(1L, "cmd-2", true, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publish(order, EventTypes.ORDER_CANCELLED, "Stock released");
    }

    @Test
    void onPaymentEvents_confirmOrTriggerCancellationOrRecordOrphanedMetric() {
        Order awaiting = order(1L, OrderStatus.AWAITING_PAYMENT);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(awaiting));
        service.onPaymentSucceeded(1L, "pay_123");
        assertThat(awaiting.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(events).publish(awaiting, EventTypes.ORDER_CONFIRMED, "Payment received (pay_123)");

        Order cancelled = order(2L, OrderStatus.CANCELLED);
        when(orders.findWithItemsById(2L)).thenReturn(Optional.of(cancelled));
        service.onPaymentSucceeded(2L, "pay_late");
        verify(metrics).paymentOrphaned();

        Order awaitingFail = order(3L, OrderStatus.AWAITING_PAYMENT);
        when(orders.findWithItemsById(3L)).thenReturn(Optional.of(awaitingFail));
        service.onPaymentFailed(3L, "Insufficient funds");
        assertThat(awaitingFail.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        verify(events).publish(awaitingFail, EventTypes.ORDER_CANCEL_REQUESTED, "Payment failed: Insufficient funds");
    }

    @Test
    void expire_cancelsPendingOrAwaitingPaymentAndSkipsOthers() {
        Order pending = order(1L, OrderStatus.PENDING);
        Order confirmed = order(2L, OrderStatus.CONFIRMED);
        when(orders.findWithItemsById(1L)).thenReturn(Optional.of(pending));
        when(orders.findWithItemsById(2L)).thenReturn(Optional.of(confirmed));
        when(orders.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThat(service.expire(1L)).isTrue();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);

        assertThat(service.expire(2L)).isFalse();
        assertThat(service.expire(99L)).isFalse();
    }

    @Test
    void missingOrder_throwsOrderNotFoundForCommandsAndMissingOrderForEvents() {
        when(orders.findWithItemsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markShipped(404L, "admin:1"))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.onInventoryReserved(404L))
                .isInstanceOf(MissingOrderException.class);
    }
}
