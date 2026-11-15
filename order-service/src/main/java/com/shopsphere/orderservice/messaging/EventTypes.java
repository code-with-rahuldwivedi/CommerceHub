package com.shopsphere.orderservice.messaging;

/** Names of the events on the wire. Adding an event = adding a constant here and documenting it in docs/EVENTS.md. */
public final class EventTypes {

    private EventTypes() {
    }

    // ---- published by order-service on the versioned topic (order.events.v1) ----
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_AWAITING_PAYMENT = "ORDER_AWAITING_PAYMENT";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_FAILED = "ORDER_FAILED";
    public static final String ORDER_CANCEL_REQUESTED = "ORDER_CANCEL_REQUESTED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_CANCEL_FAILED = "ORDER_CANCEL_FAILED";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    // ---- legacy stream (original single-item commands for the existing inventory-service) ----
    public static final String LEGACY_ORDER_CREATED = "ORDER_CREATED";

    // ---- consumed from other services (envelope events) ----
    public static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
    public static final String INVENTORY_RESERVATION_FAILED = "INVENTORY_RESERVATION_FAILED";
    public static final String INVENTORY_RELEASED = "INVENTORY_RELEASED";
    public static final String INVENTORY_RELEASE_FAILED = "INVENTORY_RELEASE_FAILED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
}
