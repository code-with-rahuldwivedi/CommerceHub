package com.shopsphere.orderservice.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStateMachine;
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

/**
 * The ONLY component that changes an order's status. Every change: checks the state machine, writes the
 * history row and queues the outbox event in the same transaction. The API, the Kafka consumers and the
 * scheduled jobs all go through here, so the rules cannot drift apart. Adding a new participant (shipping,
 * loyalty, ...) means adding one method here and one inbound handler, not touching the existing flows.
 */
@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    static final String ACTOR_INVENTORY = "system:inventory";
    static final String ACTOR_PAYMENT = "system:payment";
    static final String ACTOR_EXPIRY = "system:expiry";

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderEvents events;
    private final OrderProperties properties;
    private final OrderMetrics metrics;
    private final Clock clock;

    public OrderLifecycleService(OrderRepository orders, OrderStatusHistoryRepository history, OrderEvents events,
                                 OrderProperties properties, OrderMetrics metrics, Clock clock) {
        this.orders = orders;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ commands (API)

    /**
     * Stores a new order (status PENDING) and queues ORDER_PLACED. Flushes immediately so a duplicate
     * (user, Idempotency-Key) fails here, inside this call.
     */
    @Transactional
    public Order place(Order order, String actor) {
        order.setStatus(OrderStatus.PENDING);
        Order saved = orders.saveAndFlush(order);
        recordHistory(saved.getId(), null, OrderStatus.PENDING, null, actor);
        events.publish(saved, EventTypes.ORDER_PLACED, null);
        metrics.transition(null, OrderStatus.PENDING);
        return saved;
    }

    /** Customer / admin cancellation. Idempotent for orders that are already being cancelled or cancelled. */
    @Transactional
    public Order requestCancellation(long orderId, String reason, String actor) {
        Order order = loadForCommand(orderId);
        switch (order.getStatus()) {
            case CANCELLED, CANCEL_REQUESTED -> {
                return order;
            }
            case CONFIRMED, AWAITING_PAYMENT, CANCEL_FAILED -> {
                // allowed
            }
            default -> throw new InvalidOrderStateException(
                    "Order cannot be cancelled in status " + order.getStatus());
        }
        startCancellation(order, reason == null || reason.isBlank() ? "Cancelled on request" : reason.trim(), actor);
        return order;
    }

    @Transactional
    public Order markShipped(long orderId, String actor) {
        Order order = loadForCommand(orderId);
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            return order;
        }
        transition(order, OrderStatus.SHIPPED, null, actor, EventTypes.ORDER_SHIPPED);
        return order;
    }

    @Transactional
    public Order markDelivered(long orderId, String actor) {
        Order order = loadForCommand(orderId);
        if (order.getStatus() == OrderStatus.DELIVERED) {
            return order;
        }
        transition(order, OrderStatus.DELIVERED, null, actor, EventTypes.ORDER_DELIVERED);
        return order;
    }

    // ------------------------------------------------------------------ reactions to other services

    /** Stock was reserved. Stale / duplicate results are ignored. */
    @Transactional
    public void onInventoryReserved(long orderId) {
        Order order = loadForEvent(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            ignored("inventory reserved", order);
            return;
        }
        if (properties.payment().enabled()) {
            transition(order, OrderStatus.AWAITING_PAYMENT, "Inventory reserved", ACTOR_INVENTORY,
                    EventTypes.ORDER_AWAITING_PAYMENT);
        } else {
            transition(order, OrderStatus.CONFIRMED, "Inventory reserved", ACTOR_INVENTORY,
                    EventTypes.ORDER_CONFIRMED);
        }
    }

    @Transactional
    public void onInventoryReservationFailed(long orderId, String reason) {
        Order order = loadForEvent(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            ignored("inventory reservation failed", order);
            return;
        }
        transition(order, OrderStatus.FAILED, trim(reason, "Inventory reservation failed"), ACTOR_INVENTORY,
                EventTypes.ORDER_FAILED);
    }

    /** Result of a cancellation command. Only the result of the CURRENT command counts. */
    @Transactional
    public void onCancellationResult(long orderId, String commandId, boolean success, String reason) {
        Order order = loadForEvent(orderId);
        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED
                || commandId == null
                || !Objects.equals(commandId, order.getCancellationCommandId())) {
            log.debug("Ignoring stale cancellation result. orderId={}, commandId={}", orderId, commandId);
            return;
        }
        if (success) {
            transition(order, OrderStatus.CANCELLED, trim(reason, "Stock released"), ACTOR_INVENTORY,
                    EventTypes.ORDER_CANCELLED);
        } else {
            transition(order, OrderStatus.CANCEL_FAILED, trim(reason, "Stock release failed"), ACTOR_INVENTORY,
                    EventTypes.ORDER_CANCEL_FAILED);
        }
    }

    @Transactional
    public void onPaymentSucceeded(long orderId, String paymentId) {
        Order order = loadForEvent(orderId);
        switch (order.getStatus()) {
            case AWAITING_PAYMENT -> transition(order, OrderStatus.CONFIRMED,
                    "Payment received" + (paymentId == null ? "" : " (" + paymentId + ")"), ACTOR_PAYMENT,
                    EventTypes.ORDER_CONFIRMED);
            case CANCEL_REQUESTED, CANCELLED, CANCEL_FAILED, FAILED -> {
                // Money was taken for an order that can no longer be fulfilled: somebody must refund it.
                metrics.paymentOrphaned();
                log.error("ALERT payment succeeded for order {} in status {} (payment {}): refund required",
                        orderId, order.getStatus(), paymentId);
            }
            default -> ignored("payment succeeded", order);
        }
    }

    /** A failed payment releases the reserved stock through the normal cancellation flow. */
    @Transactional
    public void onPaymentFailed(long orderId, String reason) {
        Order order = loadForEvent(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            ignored("payment failed", order);
            return;
        }
        startCancellation(order, "Payment failed: " + trim(reason, "declined"), ACTOR_PAYMENT);
    }

    // ------------------------------------------------------------------ scheduled jobs

    /** Gives up on an order stuck in PENDING / AWAITING_PAYMENT and releases anything reserved for it. */
    @Transactional
    public boolean expire(long orderId) {
        Order order = orders.findWithItemsById(orderId).orElse(null);
        if (order == null
                || (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT)) {
            return false;
        }
        startCancellation(order, "Expired while " + order.getStatus(), ACTOR_EXPIRY);
        return true;
    }

    // ------------------------------------------------------------------ internals

    private void startCancellation(Order order, String reason, String actor) {
        order.setCancellationCommandId(UUID.randomUUID().toString());
        order.setCancellationReason(trim(reason, null));
        transition(order, OrderStatus.CANCEL_REQUESTED, reason, actor, EventTypes.ORDER_CANCEL_REQUESTED);
    }

    private void transition(Order order, OrderStatus to, String reason, String actor, String eventType) {
        OrderStatus from = order.getStatus();
        if (!OrderStateMachine.canTransition(from, to)) {
            throw new InvalidOrderStateException("Cannot move an order from " + from + " to " + to);
        }
        order.setStatus(to);
        orders.saveAndFlush(order); // fails here, not at commit, if another actor changed the order meanwhile
        recordHistory(order.getId(), from, to, reason, actor);
        events.publish(order, eventType, reason);
        metrics.transition(from, to);
        log.info("Order {} {} -> {} by {}", order.getId(), from, to, actor);
    }

    private void recordHistory(Long orderId, OrderStatus from, OrderStatus to, String reason, String actor) {
        history.save(new OrderStatusHistory(orderId, from, to, trim(reason, null), actor, LocalDateTime.now(clock)));
    }

    private Order loadForCommand(long orderId) {
        return orders.findWithItemsById(orderId).orElseThrow(OrderNotFoundException::new);
    }

    private Order loadForEvent(long orderId) {
        return orders.findWithItemsById(orderId).orElseThrow(() -> new MissingOrderException(orderId));
    }

    private void ignored(String what, Order order) {
        log.debug("Ignoring duplicate/stale '{}' for order {} in status {}", what, order.getId(), order.getStatus());
    }

    private static String trim(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > 250 ? trimmed.substring(0, 250) : trimmed;
    }
}
