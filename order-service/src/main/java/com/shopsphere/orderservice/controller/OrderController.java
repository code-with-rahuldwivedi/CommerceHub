package com.shopsphere.orderservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shopsphere.orderservice.dto.CancelRequest;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.StatusHistoryResponse;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.security.CurrentUser;
import com.shopsphere.orderservice.service.OrderQueryService;
import com.shopsphere.orderservice.service.OrderService;
import com.shopsphere.orderservice.service.PlacementResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Customer-facing order API. The customer is always taken from the access token. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;

    public OrderController(OrderService orderService, OrderQueryService queryService) {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    /**
     * 201 + Location for a new order; 200 + "Idempotent-Replayed: true" when the same Idempotency-Key already
     * created this order (safe to retry after a timeout); 409 if the key was used for a different request.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderRequest request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        CurrentUser caller = caller();
        PlacementResult result = orderService.place(caller.userId(), request, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.order().id()).toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable @Positive long id) {
        return queryService.get(id, caller());
    }

    /** My orders, newest first. */
    @GetMapping
    public PagedModel<OrderResponse> listMine(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queryService.listMine(caller().userId(), status, page, size));
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable @Positive long id) {
        return queryService.history(id, caller());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable @Positive long id,
                                @RequestBody(required = false) @Valid CancelRequest body) {
        return orderService.cancel(id, caller(), body == null ? null : body.reason());
    }

    private static CurrentUser caller() {
        CurrentUser caller = CurrentUser.fromContext();
        if (caller.userId() == null) {
            throw new AccessDeniedException("Authenticated user required");
        }
        return caller;
    }
}
