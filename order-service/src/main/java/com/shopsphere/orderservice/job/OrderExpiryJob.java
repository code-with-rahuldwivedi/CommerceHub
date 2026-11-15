package com.shopsphere.orderservice.job;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.service.OrderLifecycleService;

/**
 * Saga timeout: an order that stays PENDING (no stock answer) or AWAITING_PAYMENT too long is cancelled through the
 * normal cancellation flow, which also releases any stock that was reserved late. Several replicas may run it:
 * optimistic locking makes the loser skip the order.
 */
@Component
@ConditionalOnProperty(prefix = "orders.expiry", name = "enabled", havingValue = "true")
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderExpiryJob(OrderRepository orders, OrderLifecycleService lifecycle, OrderProperties properties,
                          Clock clock) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orders.expiry.interval-ms}")
    public void expireStaleOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        expire(OrderStatus.PENDING, now.minus(properties.expiry().pendingTimeout()));
        expire(OrderStatus.AWAITING_PAYMENT, now.minus(properties.expiry().awaitingPaymentTimeout()));
    }

    private void expire(OrderStatus status, LocalDateTime cutoff) {
        List<Order> stale = orders.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(status, cutoff);
        for (Order order : stale) {
            try {
                if (lifecycle.expire(order.getId())) {
                    log.warn("Order {} expired in status {}", order.getId(), status);
                }
            } catch (ConcurrencyFailureException e) {
                log.debug("Order {} changed while expiring, skipped", order.getId());
            } catch (RuntimeException e) {
                log.error("Could not expire order {}", order.getId(), e);
            }
        }
    }
}
