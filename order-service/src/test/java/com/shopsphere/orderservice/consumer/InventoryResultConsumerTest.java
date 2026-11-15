package com.shopsphere.orderservice.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.exception.MissingOrderException;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.service.OrderLifecycleService;

class InventoryResultConsumerTest {

    private OrderRepository orderRepository;
    private OrderLifecycleService lifecycle;
    private InventoryResultConsumer consumer;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        lifecycle = mock(OrderLifecycleService.class);
        consumer = new InventoryResultConsumer(new ObjectMapper(), orderRepository, lifecycle);
    }

    private static Order singleItemOrder(long id, long productId, int qty) {
        Order order = new Order();
        order.setId(id);
        order.addItem(new OrderItem(productId, "Item", BigDecimal.TEN, qty));
        return order;
    }

    @Test
    void consumeInventoryResult_appliesAllFourLegacyStatuses() {
        when(orderRepository.findWithItemsById(10L)).thenReturn(Optional.of(singleItemOrder(10L, 5L, 2)));

        consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":2,\"status\":\"INVENTORY_RESERVED\"}");
        verify(lifecycle).onInventoryReserved(10L);

        consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":2,\"status\":\"INVENTORY_RESERVATION_FAILED\",\"message\":\"OOS\"}");
        verify(lifecycle).onInventoryReservationFailed(10L, "OOS");

        consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":2,\"status\":\"ORDER_CANCELLED\",\"commandId\":\"cmd-1\",\"message\":\"ok\"}");
        verify(lifecycle).onCancellationResult(10L, "cmd-1", true, "ok");

        consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":2,\"status\":\"ORDER_CANCELLATION_FAILED\",\"commandId\":\"cmd-2\",\"message\":\"err\"}");
        verify(lifecycle).onCancellationResult(10L, "cmd-2", false, "err");
    }

    @Test
    void consumeInventoryResult_rejectsInvalidPayloadsMismatchedOrderOrMissingOrder() {
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{bad-json"))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{\"orderId\":0,\"productId\":5,\"quantity\":1,\"status\":\"INVENTORY_RESERVED\"}"))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":1,\"status\":\"UNKNOWN\"}"))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":1,\"status\":\"ORDER_CANCELLED\"}"))
                .isInstanceOf(InvalidEventException.class);

        when(orderRepository.findWithItemsById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{\"orderId\":99,\"productId\":5,\"quantity\":1,\"status\":\"INVENTORY_RESERVED\"}"))
                .isInstanceOf(MissingOrderException.class);

        when(orderRepository.findWithItemsById(10L)).thenReturn(Optional.of(singleItemOrder(10L, 5L, 2)));
        assertThatThrownBy(() -> consumer.consumeInventoryResult("{\"orderId\":10,\"productId\":5,\"quantity\":9,\"status\":\"INVENTORY_RESERVED\"}"))
                .isInstanceOf(InvalidEventException.class);
    }
}
