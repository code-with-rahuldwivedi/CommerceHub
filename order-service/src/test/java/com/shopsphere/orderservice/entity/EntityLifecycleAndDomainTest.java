package com.shopsphere.orderservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class EntityLifecycleAndDomainTest {

    @Test
    void order_onCreateAndOnUpdate_setsDefaultsAndTimestamps() {
        Order order = new Order();
        order.setUserId(10L);
        order.setCurrency("INR");

        order.onCreate();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderDate()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();

        LocalDateTime initialDate = LocalDateTime.of(2026, 1, 1, 10, 0);
        order.setOrderDate(initialDate);
        order.setStatus(OrderStatus.CONFIRMED);
        order.onCreate();
        assertThat(order.getOrderDate()).isEqualTo(initialDate);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        order.onUpdate();
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getOrderDate());
    }

    @Test
    void order_addItem_linksBiDirectionallyAndReturnsUnmodifiableList() {
        Order order = new Order();
        OrderItem item = new OrderItem(5L, "Keyboard", new BigDecimal("1250.50"), 2);
        order.addItem(item);

        assertThat(item.getLineTotal()).isEqualByComparingTo("2501.00");
        assertThat(item.getProductId()).isEqualTo(5L);
        assertThat(item.getProductName()).isEqualTo("Keyboard");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("1250.50");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(order.getItems()).hasSize(1);
        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void address_gettersReturnAllFields() {
        Address addr = new Address("Jane Doe", "123 Main St", "Apt 4B", "Bengaluru", "KA", "560001", "IN", "+919876543210");
        assertThat(addr.getFullName()).isEqualTo("Jane Doe");
        assertThat(addr.getLine1()).isEqualTo("123 Main St");
        assertThat(addr.getLine2()).isEqualTo("Apt 4B");
        assertThat(addr.getCity()).isEqualTo("Bengaluru");
        assertThat(addr.getState()).isEqualTo("KA");
        assertThat(addr.getPostalCode()).isEqualTo("560001");
        assertThat(addr.getCountry()).isEqualTo("IN");
        assertThat(addr.getPhone()).isEqualTo("+919876543210");
    }

    @Test
    void outboxEvent_onCreate_setsDefaults() {
        OutboxEvent event = new OutboxEvent();
        event.setStatus(null);
        event.setAttempts(null);
        event.onCreate();

        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.PENDING);
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void inboxEvent_and_orderStatusHistory_gettersWork() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 12, 0);
        InboxEvent inbox = new InboxEvent("evt-1", "inventory", now);
        assertThat(inbox.getEventId()).isEqualTo("evt-1");
        assertThat(inbox.getConsumer()).isEqualTo("inventory");
        assertThat(inbox.getProcessedAt()).isEqualTo(now);

        OrderStatusHistory hist = new OrderStatusHistory(99L, OrderStatus.PENDING, OrderStatus.CONFIRMED, "ok", "system:inventory", now);
        assertThat(hist.getOrderId()).isEqualTo(99L);
        assertThat(hist.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(hist.getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(hist.getReason()).isEqualTo("ok");
        assertThat(hist.getActor()).isEqualTo("system:inventory");
        assertThat(hist.getCreatedAt()).isEqualTo(now);
    }
}
