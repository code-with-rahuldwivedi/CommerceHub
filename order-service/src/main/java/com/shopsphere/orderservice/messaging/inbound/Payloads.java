package com.shopsphere.orderservice.messaging.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.orderservice.exception.InvalidEventException;

/** Strict, small helpers for reading event payloads. */
public final class Payloads {

    private Payloads() {
    }

    public static long requirePositiveLong(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        if (node == null || !node.canConvertToLong() || node.asLong() <= 0) {
            throw new InvalidEventException("Payload field '" + field + "' must be a positive number");
        }
        return node.asLong();
    }

    public static String requireText(JsonNode payload, String field) {
        String value = optionalText(payload, field);
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Payload field '" + field + "' is required");
        }
        return value;
    }

    public static String optionalText(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
