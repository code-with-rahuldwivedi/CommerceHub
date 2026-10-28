package com.shopsphere.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.dto.OrderCreatedEventPayload;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderPersistenceService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /** Saves an order and, for a new order, its creation event atomically. */
    @Transactional
    public Order saveOrder(Order order) {
        boolean isNewOrder = order.getId() == null;
        Order savedOrder = orderRepository.saveAndFlush(order);
        if (isNewOrder) {
            saveOutboxEvent(savedOrder, "ORDER_CREATED", null);
        }
        return savedOrder;
    }

    /** Saves an order state transition and its event in one DB transaction. */
    @Transactional
    public Order saveOrderWithEvent(
            Order order, String eventType, String commandId) {
        if (order.getId() == null) {
            throw new IllegalArgumentException("An existing order is required for a transition event");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        Order savedOrder = orderRepository.saveAndFlush(order);
        saveOutboxEvent(savedOrder, eventType, commandId);
        return savedOrder;
    }

    private void saveOutboxEvent(
            Order order, String eventType, String commandId) {
        OrderCreatedEventPayload payload = new OrderCreatedEventPayload(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getIdempotencyKey(),
                order.getOrderDate(),
                eventType,
                commandId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(order.getId());
        event.setEventType(eventType);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize " + eventType + " event", exception);
        }
        event.setStatus("PENDING");
        event.setAttempts(0);
        outboxEventRepository.save(event);
    }
}
