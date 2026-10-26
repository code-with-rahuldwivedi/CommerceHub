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

    @Transactional
    public Order saveOrder(Order order) {

        boolean isNewOrder = order.getId() == null;

        Order savedOrder = orderRepository.saveAndFlush(order);

        // Only create ORDER_CREATED for a brand-new order.
        // Updating CONFIRMED or CANCELLED must not create it again.
        if (isNewOrder) {
            OrderCreatedEventPayload payload =
                    new OrderCreatedEventPayload(
                            savedOrder.getId(),
                            savedOrder.getUserId(),
                            savedOrder.getProductId(),
                            savedOrder.getQuantity(),
                            savedOrder.getTotalPrice(),
                            savedOrder.getIdempotencyKey(),
                            savedOrder.getOrderDate()
                    );

            OutboxEvent event = new OutboxEvent();
            event.setAggregateType("ORDER");
            event.setAggregateId(savedOrder.getId());
            event.setEventType("ORDER_CREATED");

            try {
                event.setPayload(
                        objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Could not serialize ORDER_CREATED event",
                        exception);
            }

            event.setStatus("PENDING");
            event.setAttempts(0);

            outboxEventRepository.save(event);
        }

        return savedOrder;
    }
}
