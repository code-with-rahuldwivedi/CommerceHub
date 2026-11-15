package com.shopsphere.orderservice.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.config.OutboxProperties;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.InboxEventRepository;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.OutboxEventRepository;
import com.shopsphere.orderservice.service.OrderLifecycleService;

class JobsTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void orderExpiryJob_expiresStalePendingAndAwaitingPaymentOrdersAndToleratesConflicts() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderLifecycleService lifecycle = mock(OrderLifecycleService.class);
        OrderProperties props = new OrderProperties(
                "INR", 5, 100, false,
                new OrderProperties.Events("order.events.v1", true, "order-events"),
                new OrderProperties.Payment(true),
                new OrderProperties.Expiry(true, Duration.ofMinutes(15), Duration.ofMinutes(30), 60000),
                new OrderProperties.Inbound(true, "inventory.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10))),
                new OrderProperties.Catalog(3, Duration.ofMillis(200), 10, 50, Duration.ofSeconds(10))
        );

        Order o1 = new Order();
        o1.setId(1L);
        Order o2 = new Order();
        o2.setId(2L);
        Order o3 = new Order();
        o3.setId(3L);

        LocalDateTime now = LocalDateTime.now(clock);
        when(orders.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OrderStatus.PENDING, now.minusMinutes(15)))
                .thenReturn(List.of(o1, o2));
        when(orders.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OrderStatus.AWAITING_PAYMENT, now.minusMinutes(30)))
                .thenReturn(List.of(o3));

        when(lifecycle.expire(1L)).thenReturn(true);
        when(lifecycle.expire(2L)).thenThrow(new OptimisticLockingFailureException("race"));
        when(lifecycle.expire(3L)).thenReturn(true);

        OrderExpiryJob job = new OrderExpiryJob(orders, lifecycle, props, clock);
        job.expireStaleOrders();

        verify(lifecycle).expire(1L);
        verify(lifecycle).expire(2L);
        verify(lifecycle).expire(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retentionJob_deletesOldPublishedOutboxAndProcessedInboxRows() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        InboxEventRepository inbox = mock(InboxEventRepository.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Integer> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        OutboxProperties props = new OutboxProperties(
                new OutboxProperties.Publisher(true, 500, 50, Duration.ofMinutes(2), Duration.ofSeconds(5), 10),
                new OutboxProperties.Cleanup(true, "0 0 3 * * *", 7)
        );

        RetentionJob job = new RetentionJob(outbox, inbox, props, tx, clock);
        job.purge();

        LocalDateTime expectedCutoff = LocalDateTime.now(clock).minusDays(7);
        verify(outbox).deletePublishedBefore(expectedCutoff);
        verify(inbox).deleteProcessedBefore(expectedCutoff);
    }
}
