package com.shopsphere.orderservice.entity;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    FAILED,
    SHIPPED,
    DELIVERED,
    CANCEL_REQUESTED,
    CANCEL_FAILED,
    CANCELLED
}
