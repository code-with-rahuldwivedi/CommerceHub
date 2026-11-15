package com.shopsphere.orderservice.messaging.inbound;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.messaging.EventTypes;
import com.shopsphere.orderservice.service.OrderLifecycleService;

/**
 * Payment results from payment-service. Payload: {orderId, paymentId?, reason?}.
 * Only meaningful when "orders.payment.enabled" is true (orders then wait in AWAITING_PAYMENT); otherwise the
 * events find no order in that state and are ignored.
 */
@Component
public class PaymentEventHandler implements InboundEventHandler {

    private final OrderLifecycleService lifecycle;

    public PaymentEventHandler(OrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public String name() {
        return "payment";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(EventTypes.PAYMENT_SUCCEEDED, EventTypes.PAYMENT_FAILED);
    }

    @Override
    public void handle(String eventType, JsonNode payload, EventContext context) {
        long orderId = Payloads.requirePositiveLong(payload, "orderId");

        switch (eventType) {
            case EventTypes.PAYMENT_SUCCEEDED ->
                    lifecycle.onPaymentSucceeded(orderId, Payloads.optionalText(payload, "paymentId"));
            case EventTypes.PAYMENT_FAILED ->
                    lifecycle.onPaymentFailed(orderId, Payloads.optionalText(payload, "reason"));
            default -> throw new InvalidEventException("Unsupported payment event: " + eventType);
        }
    }
}
