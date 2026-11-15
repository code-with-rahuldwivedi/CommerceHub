package com.shopsphere.orderservice.exception;

public class InvalidEventException extends NonRetryableEventException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}