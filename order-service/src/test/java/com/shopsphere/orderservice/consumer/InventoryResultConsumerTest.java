package com.shopsphere.orderservice.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryResultConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    private InventoryResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryResultConsumer(
                new ObjectMapper(), orderRepository);
    }

    @Test
    void consumeInventoryResult_ShouldConfirmPendingOrder() throws Exception {
        Order order = createOrder(OrderStatus.PENDING);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        consumer.consumeInventoryResult(validMessage("INVENTORY_RESERVED", 3L, 2));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void consumeInventoryResult_ShouldFailOrderWhenReservationFails() throws Exception {
        Order order = createOrder(OrderStatus.PENDING);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        consumer.consumeInventoryResult(validMessage("INVENTORY_RESERVATION_FAILED", 3L, 2));

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void consumeInventoryResult_ShouldIgnoreDuplicateResult() throws Exception {
        Order order = createOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        consumer.consumeInventoryResult(validMessage("INVENTORY_RESERVED", 3L, 2));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void consumeInventoryResult_ShouldThrowWhenOrderDoesNotExist() throws Exception {
        when(orderRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(
                InventoryResultConsumer.MissingOrderException.class,
                () -> consumer.consumeInventoryResult(
                        validMessage("INVENTORY_RESERVED", 3L, 2)));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void consumeInventoryResult_ShouldRejectMismatchedProduct() throws Exception {
        Order order = createOrder(OrderStatus.PENDING);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(
                InventoryResultConsumer.InvalidInventoryResultException.class,
                () -> consumer.consumeInventoryResult(
                        validMessage("INVENTORY_RESERVED", 99L, 2)));

        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void consumeInventoryResult_ShouldRejectMismatchedQuantity() throws Exception {
        Order order = createOrder(OrderStatus.PENDING);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(
                InventoryResultConsumer.InvalidInventoryResultException.class,
                () -> consumer.consumeInventoryResult(
                        validMessage("INVENTORY_RESERVED", 3L, 5)));

        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void consumeInventoryResult_ShouldRejectUnknownStatus() {
        assertThrows(
                InventoryResultConsumer.InvalidInventoryResultException.class,
                () -> consumer.consumeInventoryResult(
                        validMessage("UNKNOWN_STATUS", 3L, 2)));
    }

    @Test
    void consumeInventoryResult_ShouldRejectMalformedJson() {
        assertThrows(
                InventoryResultConsumer.InvalidInventoryResultException.class,
                () -> consumer.consumeInventoryResult("{not-json"));
    }

    private String validMessage(String status, Long productId, int quantity) {
        return "{"
                + "\"orderId\":100,"
                + "\"productId\":" + productId + ","
                + "\"quantity\":" + quantity + ","
                + "\"status\":\"" + status + "\","
                + "\"message\":\"test\""
                + "}";
    }

    private Order createOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setUserId(7L);
        order.setProductId(3L);
        order.setQuantity(2);
        order.setTotalPrice(new BigDecimal("179998.00"));
        order.setStatus(status);
        order.setIdempotencyKey("shop-order-001");
        return order;
    }
}
