package com.shopsphere.orderservice.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.dto.OrderMapper;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.StatusHistoryResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.OrderSpecifications;
import com.shopsphere.orderservice.repository.OrderStatusHistoryRepository;
import com.shopsphere.orderservice.security.CurrentUser;

/** Read side. Everything is mapped to DTOs inside the transaction (no lazy loading outside it). */
@Service
public class OrderQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;

    public OrderQueryService(OrderRepository orders, OrderStatusHistoryRepository history) {
        this.orders = orders;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long orderId, CurrentUser caller) {
        return OrderMapper.toResponse(findVisible(orderId, caller));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(long orderId, CurrentUser caller) {
        Order order = findVisible(orderId, caller);
        return history.findByOrderIdOrderByIdAsc(order.getId()).stream().map(OrderMapper::toResponse).toList();
    }

    /** The caller's own orders, newest first. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listMine(long userId, OrderStatus status, int page, int size) {
        return orders.findAll(OrderSpecifications.matching(userId, status), pageable(page, size))
                .map(OrderMapper::toResponse);
    }

    /** Admin view: any user (optional filter), newest first. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listAll(Long userId, OrderStatus status, int page, int size) {
        return orders.findAll(OrderSpecifications.matching(userId, status), pageable(page, size))
                .map(OrderMapper::toResponse);
    }

    private Order findVisible(long orderId, CurrentUser caller) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(OrderNotFoundException::new);
        OrderService.requireOwnerOrAdmin(order, caller);
        return order;
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "orderDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
