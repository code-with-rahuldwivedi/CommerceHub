package com.shopsphere.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.StatusHistoryResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.CatalogUnavailableException;
import com.shopsphere.orderservice.exception.GlobalExceptionHandler;
import com.shopsphere.orderservice.exception.IdempotencyConflictException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.ProductUnavailableException;
import com.shopsphere.orderservice.security.CurrentUser;
import com.shopsphere.orderservice.service.OrderQueryService;
import com.shopsphere.orderservice.service.OrderService;
import com.shopsphere.orderservice.service.PlacementResult;

class OrderControllersAndExceptionHandlerTest {

    private OrderService orderService;
    private OrderQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        queryService = mock(OrderQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OrderController(orderService, queryService),
                        new AdminOrderController(orderService, queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(long userId, String role) {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "RS256").subject(String.valueOf(userId)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static OrderResponse sampleResponse(long id, OrderStatus status) {
        return new OrderResponse(id, 7L, 101L, 1, List.of(), "INR",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                status, null, null, null, LocalDateTime.now(), LocalDateTime.now(), 0L);
    }

    @Test
    void customerEndpoints_handleCreateReplayGetListHistoryCancelAndErrors() throws Exception {
        authenticate(7L, "USER");

        when(orderService.place(eq(7L), any(), eq("key-new")))
                .thenReturn(new PlacementResult(sampleResponse(10L, OrderStatus.PENDING), false));
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":101,\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/10"))
                .andExpect(jsonPath("$.id").value(10));

        when(orderService.place(eq(7L), any(), eq("key-replay")))
                .thenReturn(new PlacementResult(sampleResponse(10L, OrderStatus.PENDING), true));
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":101,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        when(queryService.get(10L, new CurrentUser(7L, false))).thenReturn(sampleResponse(10L, OrderStatus.CONFIRMED));
        mockMvc.perform(get("/api/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        when(queryService.listMine(7L, null, 0, 20)).thenReturn(new PageImpl<>(List.of(sampleResponse(10L, OrderStatus.CONFIRMED))));
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));

        when(queryService.history(10L, new CurrentUser(7L, false)))
                .thenReturn(List.of(new StatusHistoryResponse(null, OrderStatus.PENDING, null, "user:7", LocalDateTime.now())));
        mockMvc.perform(get("/api/orders/10/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("PENDING"));

        when(orderService.cancel(10L, new CurrentUser(7L, false), "Mistake"))
                .thenReturn(sampleResponse(10L, OrderStatus.CANCEL_REQUESTED));
        mockMvc.perform(post("/api/orders/10/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Mistake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));
    }

    @Test
    void adminEndpoints_and_globalExceptionHandlerProblemDetails() throws Exception {
        authenticate(1L, "ADMIN");

        when(queryService.listAll(null, OrderStatus.CONFIRMED, 0, 20))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(20L, OrderStatus.CONFIRMED))));
        mockMvc.perform(get("/api/admin/orders").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(20));

        when(orderService.markShipped(20L, new CurrentUser(1L, true))).thenReturn(sampleResponse(20L, OrderStatus.SHIPPED));
        mockMvc.perform(post("/api/admin/orders/20/ship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        when(orderService.markDelivered(20L, new CurrentUser(1L, true))).thenReturn(sampleResponse(20L, OrderStatus.DELIVERED));
        mockMvc.perform(post("/api/admin/orders/20/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        when(queryService.get(999L, new CurrentUser(1L, true))).thenThrow(new OrderNotFoundException());
        mockMvc.perform(get("/api/admin/orders/999")).andExpect(status().isNotFound());

        when(orderService.place(eq(1L), any(), eq("conflict"))).thenThrow(new IdempotencyConflictException());
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", "conflict")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isConflict());

        when(orderService.place(eq(1L), any(), eq("oos"))).thenThrow(new ProductUnavailableException(List.of(1L)));
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", "oos")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.productIds[0]").value(1));

        when(orderService.place(eq(1L), any(), eq("down"))).thenThrow(new CatalogUnavailableException("down", null));
        mockMvc.perform(post("/api/orders").header("Idempotency-Key", "down")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));

        when(orderService.markShipped(20L, new CurrentUser(1L, true)))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        mockMvc.perform(post("/api/admin/orders/20/ship")).andExpect(status().isConflict());
    }
}
