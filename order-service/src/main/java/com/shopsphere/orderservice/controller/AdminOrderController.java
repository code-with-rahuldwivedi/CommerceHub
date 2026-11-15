package com.shopsphere.orderservice.controller;

import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.security.CurrentUser;
import com.shopsphere.orderservice.service.OrderQueryService;
import com.shopsphere.orderservice.service.OrderService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Back-office API (ADMIN only). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;

    public AdminOrderController(OrderService orderService, OrderQueryService queryService) {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    @GetMapping
    public PagedModel<OrderResponse> list(
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queryService.listAll(userId, status, page, size));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable @Positive long id) {
        return queryService.get(id, CurrentUser.fromContext());
    }

    /** CONFIRMED -> SHIPPED (idempotent). */
    @PostMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable @Positive long id) {
        return orderService.markShipped(id, CurrentUser.fromContext());
    }

    /** SHIPPED -> DELIVERED (idempotent). */
    @PostMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable @Positive long id) {
        return orderService.markDelivered(id, CurrentUser.fromContext());
    }
}
