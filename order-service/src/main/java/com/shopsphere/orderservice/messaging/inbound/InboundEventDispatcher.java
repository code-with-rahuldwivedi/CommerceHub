package com.shopsphere.orderservice.messaging.inbound;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.entity.InboxEvent;
import com.shopsphere.orderservice.exception.InvalidEventException;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.InboxEventRepository;

/**
 * Receives every envelope event, routes it to the handler that owns its type and guarantees exactly-once EFFECT on
 * top of at-least-once delivery: the handler's work and the inbox row commit in one transaction, and a redelivered
 * eventId is skipped. Unknown event types are ignored (a producer may add events before we care about them).
 */
@Component
public class InboundEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InboundEventDispatcher.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_\\-]{8,64}$");
    private static final int MAX_EVENT_ID_LENGTH = 64;

    private final Map<String, InboundEventHandler> handlersByType = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final InboxEventRepository inbox;
    private final OrderMetrics metrics;
    private final Clock clock;

    public InboundEventDispatcher(List<InboundEventHandler> handlers, ObjectMapper objectMapper,
                                  InboxEventRepository inbox, OrderMetrics metrics, Clock clock) {
        for (InboundEventHandler handler : handlers) {
            for (String type : handler.eventTypes()) {
                InboundEventHandler previous = handlersByType.put(type, handler);
                if (previous != null) {
                    throw new IllegalStateException("Event type " + type + " is claimed by both "
                            + previous.name() + " and " + handler.name());
                }
            }
        }
        this.objectMapper = objectMapper;
        this.inbox = inbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void dispatch(String source, String message) {
        JsonNode root = parse(message);
        String eventId = text(root, "eventId");
        String eventType = text(root, "eventType");
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH
                || eventType == null || eventType.isBlank()) {
            throw new InvalidEventException("Envelope needs eventId (max " + MAX_EVENT_ID_LENGTH + " chars) and eventType");
        }

        InboundEventHandler handler = handlersByType.get(eventType);
        if (handler == null) {
            metrics.inbound("unrouted", "ignored");
            log.debug("No handler for event type {} from {}, ignored", eventType, source);
            return;
        }
        if (inbox.existsByEventIdAndConsumer(eventId, handler.name())) {
            metrics.inbound(handler.name(), "duplicate");
            log.debug("Duplicate event {} for {}, skipped", eventId, handler.name());
            return;
        }

        String correlationId = text(root, "correlationId");
        boolean mdcSet = correlationId != null && SAFE_ID.matcher(correlationId).matches();
        if (mdcSet) {
            MDC.put("requestId", correlationId);
        }
        try {
            handler.handle(eventType, root.get("payload"),
                    new EventContext(eventId, correlationId, text(root, "occurredAt")));
        } finally {
            if (mdcSet) {
                MDC.remove("requestId");
            }
        }
        inbox.save(new InboxEvent(eventId, handler.name(), LocalDateTime.now(clock)));
        metrics.inbound(handler.name(), "processed");
    }

    private JsonNode parse(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidEventException("Empty message");
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root == null || !root.isObject()) {
                throw new InvalidEventException("Message is not a JSON object");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed JSON message", e);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
