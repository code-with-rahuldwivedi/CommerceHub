package com.shopsphere.orderservice.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderPersistenceService orderPersistenceService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            OrderPersistenceService orderPersistenceService) {

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderPersistenceService = orderPersistenceService;
    }

    @Override
    public OrderResponse placeOrder(
            OrderRequest request,
            String idempotencyKey) {

        // Validate idempotency key.
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key is required");
        }

        // Validate order request.
        if (request == null
                || request.getUserId() == null
                || request.getUserId() <= 0
                || request.getProductId() == null
                || request.getProductId() <= 0
                || request.getQuantity() == null
                || request.getQuantity() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid user, product and positive quantity are required");
        }

        String key = idempotencyKey.trim();

        // Return an existing order when the same request is retried.
        Order existingOrder = orderRepository
                .findByIdempotencyKey(key)
                .orElse(null);

        if (existingOrder != null) {
            validateSameRequest(existingOrder, request);
            return toResponse(existingOrder);
        }

        // Fetch product details and price.
        ProductResponse product =
                productClient.getProductById(request.getProductId());

        if (product == null
                || product.getPrice() == null
                || product.getPrice().signum() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product Service returned invalid product details");
        }

        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create the order. Its initial status is PENDING.
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);
        order.setIdempotencyKey(key);

        final Order savedOrder;

        try {
            /*
             * OrderPersistenceService saves the new order and its
             * ORDER_CREATED outbox event in the same transaction.
             */
            savedOrder = orderPersistenceService.saveOrder(order);

        } catch (DataIntegrityViolationException exception) {

            /*
             * Another concurrent request may have inserted the same
             * idempotency key. Return that order if it matches.
             */
            Order concurrentOrder = orderRepository
                    .findByIdempotencyKey(key)
                    .orElseThrow(() -> exception);

            validateSameRequest(concurrentOrder, request);
            return toResponse(concurrentOrder);
        }

        /*
         * Do not call inventoryClient.reduceStock() here.
         * Inventory Service consumes ORDER_CREATED from Kafka and
         * performs the stock reservation asynchronously.
         *
         * The order remains PENDING until an inventory result event
         * updates its status.
         */
        log.info(
                "Order created and awaiting inventory reservation. orderId={}",
                savedOrder.getId());

        return toResponse(savedOrder);
    }

    @Override
    public OrderResponse cancelOrder(Long orderId) {
        // Internal compatibility method; HTTP callers use the ownership overload.
        return cancelOrder(orderId, null, true);
    }

    @Override
    public OrderResponse cancelOrder(
            Long orderId, Long requesterUserId, boolean admin) {
        if (orderId == null || orderId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid order ID is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        if (!admin && (requesterUserId == null
                || !Objects.equals(order.getUserId(), requesterUserId))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not allowed to access this order");
        }

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
            return toResponse(order);
        }

        if (order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.CANCEL_FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only confirmed orders or orders with a failed cancellation can be cancelled");
        }

        // A new command ID distinguishes a retry attempt from an older result event.
        String commandId = UUID.randomUUID().toString();
        order.setCancellationCommandId(commandId);
        order.setStatus(OrderStatus.CANCEL_REQUESTED);

        try {
            Order updated = orderPersistenceService.saveOrderWithEvent(
                    order, "ORDER_CANCEL_REQUESTED", commandId);
            log.info("Cancellation requested asynchronously. orderId={}, commandId={}",
                    updated.getId(), commandId);
            return toResponse(updated);
        } catch (RuntimeException exception) {
            log.error("Could not persist cancellation request for order {}", orderId, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cancellation request could not be persisted; retry safely", exception);
        }
    }

    private void validateSameRequest(
            Order order,
            OrderRequest request) {

        boolean sameRequest =
                Objects.equals(order.getUserId(), request.getUserId())
                && Objects.equals(order.getProductId(), request.getProductId())
                && Objects.equals(order.getQuantity(), request.getQuantity());

        if (!sameRequest) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request");
        }
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setProductId(order.getProductId());
        response.setQuantity(order.getQuantity());
        response.setTotalPrice(order.getTotalPrice());
        response.setStatus(order.getStatus());
        response.setOrderDate(order.getOrderDate());
        return response;
    }
}
