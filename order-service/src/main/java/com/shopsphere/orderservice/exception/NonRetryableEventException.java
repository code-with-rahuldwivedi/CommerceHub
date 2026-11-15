package com.shopsphere.orderservice.exception;

// Base class of inbound-event problems that must NOT be retried (malformed, unknown order, inconsistent data). */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}