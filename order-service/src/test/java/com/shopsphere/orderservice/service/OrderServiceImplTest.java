
package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.dto.StockUpdateRequest;
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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String IDEMPOTENCY_KEY = "shop-order-001";
    private static final String STOCK_REDUCE_KEY = "order-500-reduce";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderPersistenceService orderPersistenceService;

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

    private void givenNewOrder() {
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(productClient.getProductById(101L))
                .thenReturn(productResponse);
    }

    private void givenSuccessfulSave() {
        when(orderPersistenceService.saveOrder(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);

                    order.setId(500L);
                    order.setStatus(OrderStatus.PENDING);
                    order.setOrderDate(LocalDateTime.now());

                    return order;
                });
    }

    private Order createExistingOrder() {
        Order order = new Order();
        order.setId(500L);
        order.setUserId(1L);
        order.setProductId(101L);
        order.setQuantity(2);
        order.setTotalPrice(new BigDecimal("1599.98"));
        order.setIdempotencyKey(IDEMPOTENCY_KEY);
        order.setStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.now());

        return order;
    }

    @Test
    void placeOrder_ShouldCalculateTotalPriceCorrectly() {
        givenNewOrder();
        givenSuccessfulSave();

        OrderResponse response =
                orderService.placeOrder(orderRequest, IDEMPOTENCY_KEY);

        assertNotNull(response);
        assertEquals(
                0,
                new BigDecimal("1599.98")
                        .compareTo(response.getTotalPrice())
        );
    }

    @Test
    void placeOrder_ShouldSaveOrderWithCorrectFields() {
        givenNewOrder();
        givenSuccessfulSave();

        orderService.placeOrder(orderRequest, IDEMPOTENCY_KEY);

        ArgumentCaptor<Order> captor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderPersistenceService).saveOrder(captor.capture());

        Order savedOrder = captor.getValue();

        assertEquals(1L, savedOrder.getUserId());
        assertEquals(101L, savedOrder.getProductId());
        assertEquals(2, savedOrder.getQuantity());
        assertEquals(IDEMPOTENCY_KEY, savedOrder.getIdempotencyKey());

        assertEquals(
                0,
                new BigDecimal("1599.98")
                        .compareTo(savedOrder.getTotalPrice())
        );
    }

    @Test
    void placeOrder_ShouldReduceStockWithOrderSpecificKey() {
        givenNewOrder();
        givenSuccessfulSave();

        orderService.placeOrder(orderRequest, IDEMPOTENCY_KEY);

        ArgumentCaptor<StockUpdateRequest> captor =
                ArgumentCaptor.forClass(StockUpdateRequest.class);

        verify(productClient).reduceStock(
                eq(101L),
                eq(STOCK_REDUCE_KEY),
                captor.capture()
        );

        assertEquals(2, captor.getValue().getQuantity());
    }

    @Test
    void placeOrder_ShouldReturn409WhenStockIsInsufficient() {
        orderRequest.setQuantity(50);
        givenNewOrder();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(
                        orderRequest,
                        IDEMPOTENCY_KEY
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));

        verify(productClient, never())
                .reduceStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );
    }

    @Test
    void placeOrder_ShouldReturnExistingOrderForSameKeyAndRequest() {
        Order existingOrder = createExistingOrder();

        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingOrder));

        OrderResponse response =
                orderService.placeOrder(orderRequest, IDEMPOTENCY_KEY);

        assertEquals(500L, response.getId());
        assertEquals(101L, response.getProductId());
        assertEquals(2, response.getQuantity());

        verify(productClient, never()).getProductById(anyLong());

        verify(productClient, never())
                .reduceStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );

        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));
    }

    @Test
    void placeOrder_ShouldReturn409WhenKeyIsReusedWithDifferentRequest() {
        Order existingOrder = createExistingOrder();

        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingOrder));

        orderRequest.setQuantity(5);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(
                        orderRequest,
                        IDEMPOTENCY_KEY
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(productClient, never())
                .reduceStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );

        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));
    }

    @Test
    void placeOrder_ShouldNotReduceStockWhenOrderSaveFails() {
        givenNewOrder();

        RuntimeException databaseFailure =
                new RuntimeException("Order database failure");

        when(orderPersistenceService.saveOrder(any(Order.class)))
                .thenThrow(databaseFailure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> orderService.placeOrder(
                        orderRequest,
                        IDEMPOTENCY_KEY
                )
        );

        assertSame(databaseFailure, thrown);

        verify(productClient, never())
                .reduceStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );

        verify(productClient, never())
                .restoreStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );
    }

    @Test
    void placeOrder_ShouldReturn503WhenStockReductionFails() {
        givenNewOrder();
        givenSuccessfulSave();

        when(productClient.reduceStock(
                eq(101L),
                eq(STOCK_REDUCE_KEY),
                any(StockUpdateRequest.class)
        )).thenThrow(new RuntimeException("Product service unavailable"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(
                        orderRequest,
                        IDEMPOTENCY_KEY
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        verify(orderPersistenceService).saveOrder(any(Order.class));

        verify(productClient).reduceStock(
                eq(101L),
                eq(STOCK_REDUCE_KEY),
                any(StockUpdateRequest.class)
        );

        // An uncertain stock result must not trigger a blind restoration.
        verify(productClient, never())
                .restoreStock(
                        anyLong(),
                        anyString(),
                        any(StockUpdateRequest.class)
                );
    }

    @Test
    void placeOrder_ShouldMapSavedOrderToResponse() {
        givenNewOrder();
        givenSuccessfulSave();

        OrderResponse response =
                orderService.placeOrder(orderRequest, IDEMPOTENCY_KEY);

        assertEquals(500L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(101L, response.getProductId());
        assertEquals(2, response.getQuantity());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertNotNull(response.getOrderDate());
    }

    @Test
    void placeOrder_ShouldRejectBlankIdempotencyKey() {
        assertThrows(
                ResponseStatusException.class,
                () -> orderService.placeOrder(orderRequest, " ")
        );

        verifyNoInteractions(productClient);
        verify(orderPersistenceService, never())
                .saveOrder(any(Order.class));
    }
}
