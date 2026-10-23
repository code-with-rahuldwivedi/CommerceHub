package com.shopsphere.orderservice.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.orderservice.client.InventoryClient;
import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.dto.StockUpdateRequest;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

import feign.FeignException;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderPersistenceService orderPersistenceService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            InventoryClient inventoryClient,
            OrderPersistenceService orderPersistenceService) {

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.orderPersistenceService = orderPersistenceService;
    }

    @Override
    public OrderResponse placeOrder(
            OrderRequest request,
            String idempotencyKey) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key is required");
        }

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

        Order existing = orderRepository
                .findByIdempotencyKey(key)
                .orElse(null);

        if (existing != null) {
            validateSameRequest(existing, request);
            return toResponse(existing);
        }

        // Product Service is used only for product details and price.
        ProductResponse product =
                productClient.getProductById(request.getProductId());

        if (product == null || product.getPrice() == null
                || product.getPrice().signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product Service returned invalid product details");
        }

        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);
        order.setIdempotencyKey(key);

        final Order savedOrder;

        try {
            savedOrder = orderPersistenceService.saveOrder(order);
        } catch (DataIntegrityViolationException exception) {
            Order concurrentOrder = orderRepository
                    .findByIdempotencyKey(key)
                    .orElseThrow(() -> exception);

            validateSameRequest(concurrentOrder, request);
            return toResponse(concurrentOrder);
        }

        String reduceKey = "order-" + savedOrder.getId() + "-reduce";

        try {
            // Inventory Service owns stock reduction.
            inventoryClient.reduceStock(
                    request.getProductId(),
                    reduceKey,
                    new StockUpdateRequest(request.getQuantity()));

            savedOrder.setStatus(OrderStatus.CONFIRMED);

            Order confirmedOrder =
                    orderPersistenceService.saveOrder(savedOrder);

            return toResponse(confirmedOrder);

        } catch (FeignException exception) {
            log.error("reduceStock failed for order {}: status={}, body={}",
                    savedOrder.getId(),
                    exception.status(),
                    safeBody(exception),
                    exception);

            // Inventory's atomic update is the authority on availability.
            if (exception.status() == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock or conflicting inventory operation",
                        exception);
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory operation outcome is uncertain. "
                            + "Order requires reconciliation.",
                    exception);

        } catch (RuntimeException exception) {
            log.error("reduceStock failed for order {}",
                    savedOrder.getId(), exception);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory operation outcome is uncertain. "
                            + "Order requires reconciliation.",
                    exception);
        }
    }

    @Override
    public OrderResponse cancelOrder(Long orderId) {

        if (orderId == null || orderId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid order ID is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId));

        // A retry must never restore stock twice.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only confirmed orders can be cancelled");
        }

        String restoreKey = "order-" + order.getId() + "-restore";

        try {
            // Inventory Service owns stock restoration.
            inventoryClient.restoreStock(
                    order.getProductId(),
                    restoreKey,
                    new StockUpdateRequest(order.getQuantity()));

        } catch (FeignException exception) {
            log.error("restoreStock failed for order {}: status={}, body={}",
                    order.getId(),
                    exception.status(),
                    safeBody(exception),
                    exception);

            if (exception.status() == 409) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Conflicting inventory operation",
                        exception);
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock restoration outcome is uncertain. "
                            + "Retry cancellation using the same order ID.",
                    exception);

        } catch (RuntimeException exception) {
            log.error("restoreStock failed for order {}",
                    order.getId(), exception);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock restoration outcome is uncertain. "
                            + "Retry cancellation using the same order ID.",
                    exception);
        }

        order.setStatus(OrderStatus.CANCELLED);

        try {
            Order cancelledOrder =
                    orderPersistenceService.saveOrder(order);

            return toResponse(cancelledOrder);

        } catch (RuntimeException exception) {
            log.error("Stock restored but saving CANCELLED status failed "
                    + "for order {}", order.getId(), exception);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock may have been restored, but cancellation "
                            + "could not be saved. Retry the cancellation.",
                    exception);
        }
    }

    private String safeBody(FeignException exception) {
        try {
            return exception.contentUTF8();
        } catch (RuntimeException ignored) {
            return "<unavailable>";
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