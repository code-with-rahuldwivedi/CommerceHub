package com.shopsphere.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.orderservice.client.InventoryClient;
import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.dto.StockUpdateRequest;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String IDEMPOTENCY_KEY = "shop-order-001";
    private static final String RESTORE_KEY = "order-100-restore";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private OrderRequest request;
    private ProductResponse product;

    @BeforeEach
    void setUp() {
        request = new OrderRequest();
        request.setUserId(7L);
        request.setProductId(3L);
        request.setQuantity(2);

        product = new ProductResponse();
        product.setId(3L);
        product.setName("iPhone 17");
        product.setPrice(new BigDecimal("89999.00"));
    }

    @Test
    void placeOrder_ShouldCreatePendingOrderAndSaveOnce() {
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(productClient.getProductById(3L))
                .thenReturn(product);

        when(orderPersistenceService.saveOrder(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(100L);

                    if (order.getStatus() == null) {
                        order.setStatus(OrderStatus.PENDING);
                    }

                    return order;
                });

        OrderResponse response =
                orderService.placeOrder(request, IDEMPOTENCY_KEY);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(
                new BigDecimal("179998.00"),
                response.getTotalPrice());

        verify(orderPersistenceService, times(1))
                .saveOrder(any(Order.class));
    }

    @Test
    void placeOrder_ShouldRejectBlankIdempotencyKey() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(request, " "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        verify(productClient, never()).getProductById(any());
    }

    @Test
    void placeOrder_ShouldRejectNullIdempotencyKey() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void placeOrder_ShouldRejectLongIdempotencyKey() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(request, "a".repeat(101)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void placeOrder_ShouldRejectInvalidQuantity() {
        request.setQuantity(0);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(request, IDEMPOTENCY_KEY));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        verify(productClient, never()).getProductById(any());
    }

    @Test
    void placeOrder_ShouldReplayExistingOrderForSameRequest() {
        Order existing = createOrder(100L, OrderStatus.CONFIRMED);

        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        OrderResponse response =
                orderService.placeOrder(request, IDEMPOTENCY_KEY);

        assertEquals(100L, response.getId());
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());

        verify(productClient, never()).getProductById(any());
        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));
    }

    @Test
    void placeOrder_ShouldRejectDifferentRequestForExistingKey() {
        Order existing = createOrder(100L, OrderStatus.CONFIRMED);
        existing.setQuantity(1);

        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(request, IDEMPOTENCY_KEY));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void placeOrder_ShouldReplayConcurrentInsertForSameRequest() {
        Order concurrentOrder =
                createOrder(100L, OrderStatus.PENDING);

        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentOrder));

        when(productClient.getProductById(3L))
                .thenReturn(product);

        when(orderPersistenceService.saveOrder(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate idempotency key"));

        OrderResponse response =
                orderService.placeOrder(request, IDEMPOTENCY_KEY);

        assertEquals(100L, response.getId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }

    @Test
    void cancelOrder_ShouldRestoreInventoryAndSaveCancelledStatus() {
        Order order = createOrder(100L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(100L))
                .thenReturn(Optional.of(order));

        when(orderPersistenceService.saveOrder(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(100L);

        assertEquals(OrderStatus.CANCELLED, response.getStatus());

        verify(inventoryClient).restoreStock(
                eq(3L),
                eq(RESTORE_KEY),
                any(StockUpdateRequest.class));

        verify(orderPersistenceService).saveOrder(order);
    }

    @Test
    void cancelOrder_ShouldNotRestoreInventoryTwiceWhenAlreadyCancelled() {
        Order order = createOrder(100L, OrderStatus.CANCELLED);

        when(orderRepository.findById(100L))
                .thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(100L);

        assertEquals(OrderStatus.CANCELLED, response.getStatus());

        verify(inventoryClient, never())
                .restoreStock(any(), any(), any());

        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));
    }

    @Test
    void cancelOrder_ShouldReturn404WhenOrderDoesNotExist() {
        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

        verify(inventoryClient, never())
                .restoreStock(any(), any(), any());
    }

    @Test
    void cancelOrder_ShouldRejectInvalidOrderId() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(0L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void cancelOrder_ShouldRejectNonConfirmedOrder() {
        Order order = createOrder(100L, OrderStatus.PENDING);

        when(orderRepository.findById(100L))
                .thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(100L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        verify(inventoryClient, never())
                .restoreStock(any(), any(), any());
    }

    @Test
    void cancelOrder_ShouldReturn503WhenInventoryRestoreFails() {
        Order order = createOrder(100L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(100L))
                .thenReturn(Optional.of(order));

        doThrow(new RuntimeException("Inventory unavailable"))
                .when(inventoryClient)
                .restoreStock(
                        eq(3L),
                        eq(RESTORE_KEY),
                        any(StockUpdateRequest.class));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(100L));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getStatusCode());

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    private Order createOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(7L);
        order.setProductId(3L);
        order.setQuantity(2);
        order.setTotalPrice(new BigDecimal("179998.00"));
        order.setStatus(status);
        order.setIdempotencyKey(IDEMPOTENCY_KEY);

        return order;
    }
}
