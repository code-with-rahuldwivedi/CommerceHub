package com.shopsphere.orderservice.exception;

public class MissingOrderException extends NonRetryableEventException {

    public MissingOrderException(long orderId) {
        super("Order not found for inbound event: " + orderId);
    }
}