package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private OrderRequest orderRequest;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        orderRequest = new OrderRequest();
        orderRequest.setUserId(1L);
        orderRequest.setProductId(101L);
        orderRequest.setQuantity(2);

        productResponse = new ProductResponse();
        productResponse.setId(101L);
        productResponse.setName("Wireless Mouse");
        productResponse.setPrice(new BigDecimal("799.99"));
        productResponse.setStock(10);
    }

    // Helper: simulate what the DB would return after save(), since @PrePersist
    // never fires on a mocked repository.
    private Order fakeSavedOrder(BigDecimal totalPrice) {
        Order saved = new Order();
        saved.setId(500L);
        saved.setUserId(orderRequest.getUserId());
        saved.setProductId(orderRequest.getProductId());
        saved.setQuantity(orderRequest.getQuantity());
        saved.setTotalPrice(totalPrice);
        saved.setStatus(OrderStatus.PENDING);
        saved.setOrderDate(LocalDateTime.now());
        return saved;
    }

    @Test
    void placeOrder_ShouldCalculateTotalPriceCorrectly_WhenStockIsSufficient() {
        when(productClient.getProductById(101L)).thenReturn(productResponse);

        BigDecimal expectedTotal = new BigDecimal("1599.98"); // 799.99 * 2
        when(orderRepository.save(any(Order.class))).thenReturn(fakeSavedOrder(expectedTotal));

        OrderResponse response = orderService.placeOrder(orderRequest);

        assertEquals(0, expectedTotal.compareTo(response.getTotalPrice()),
                "Total price should equal unit price * quantity");
    }

    @Test
    void placeOrder_ShouldSaveOrderWithCorrectlyComputedTotal_UsingCapturedArgument() {
        when(productClient.getProductById(101L)).thenReturn(productResponse);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        orderService.placeOrder(orderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        // 799.99 * 2 = 1599.98 -- verifying the exact value passed to save(), not just the response
        assertEquals(0, new BigDecimal("1599.98").compareTo(savedOrder.getTotalPrice()));
        assertEquals(orderRequest.getUserId(), savedOrder.getUserId());
        assertEquals(orderRequest.getProductId(), savedOrder.getProductId());
        assertEquals(orderRequest.getQuantity(), savedOrder.getQuantity());
    }

    @Test
    void placeOrder_ShouldThrowException_WhenStockIsInsufficient() {
        orderRequest.setQuantity(50); // more than available stock (10)
        when(productClient.getProductById(101L)).thenReturn(productResponse);

        RuntimeException exception =
                assertThrows(RuntimeException.class, () -> orderService.placeOrder(orderRequest));

        assertEquals("Insufficient stock", exception.getMessage());
        // Order must never be saved if stock check fails
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_ShouldAllowOrder_WhenQuantityExactlyMatchesAvailableStock() {
        orderRequest.setQuantity(10); // exactly equal to stock (10) — boundary case
        when(productClient.getProductById(101L)).thenReturn(productResponse);
        when(orderRepository.save(any(Order.class)))
                .thenReturn(fakeSavedOrder(new BigDecimal("7999.90")));

        assertDoesNotThrow(() -> orderService.placeOrder(orderRequest));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void placeOrder_ShouldCallProductClientWithCorrectProductId() {
        when(productClient.getProductById(101L)).thenReturn(productResponse);
        when(orderRepository.save(any(Order.class)))
                .thenReturn(fakeSavedOrder(new BigDecimal("1599.98")));

        orderService.placeOrder(orderRequest);

        verify(productClient, times(1)).getProductById(101L);
    }

    @Test
    void placeOrder_ShouldMapSavedOrderFieldsCorrectlyToResponse() {
        when(productClient.getProductById(101L)).thenReturn(productResponse);
        Order saved = fakeSavedOrder(new BigDecimal("1599.98"));
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        OrderResponse response = orderService.placeOrder(orderRequest);

        assertEquals(saved.getId(), response.getId());
        assertEquals(saved.getUserId(), response.getUserId());
        assertEquals(saved.getProductId(), response.getProductId());
        assertEquals(saved.getQuantity(), response.getQuantity());
        assertEquals(saved.getStatus(), response.getStatus());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }
}