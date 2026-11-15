package com.shopsphere.orderservice.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// The ONLY place that decides which order status changes are legal. 
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING,
                EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.AWAITING_PAYMENT, OrderStatus.FAILED,
                        OrderStatus.CANCEL_REQUESTED)); // CANCEL_REQUESTED: expiry releases a late reservation
        ALLOWED.put(OrderStatus.AWAITING_PAYMENT,
                EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCEL_REQUESTED));
        ALLOWED.put(OrderStatus.CONFIRMED,
                EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCEL_REQUESTED));
        ALLOWED.put(OrderStatus.CANCEL_REQUESTED,
                EnumSet.of(OrderStatus.CANCELLED, OrderStatus.CANCEL_FAILED));
        ALLOWED.put(OrderStatus.CANCEL_FAILED,
                EnumSet.of(OrderStatus.CANCEL_REQUESTED));
        ALLOWED.put(OrderStatus.SHIPPED,
                EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(OrderStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}