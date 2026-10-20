
package com.shopsphere.orderservice.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.dto.StockUpdateRequest;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.repository.OrderRepository;

@Service
public class OrderServiceImpl implements OrderService {

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

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key is required");
        }

        if (request == null
                || request.getUserId() == null
                || request.getProductId() == null
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

        ProductResponse product =
                productClient.getProductById(request.getProductId());

        if (product.getStock() < request.getQuantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient stock"
            );
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
            // Another request may have inserted the same key.
            Order concurrentOrder = orderRepository
                    .findByIdempotencyKey(key)
                    .orElseThrow(() -> exception);

            validateSameRequest(concurrentOrder, request);
            return toResponse(concurrentOrder);
        }

        String reduceKey = "order-" + savedOrder.getId() + "-reduce";

        try {
            productClient.reduceStock(
                    request.getProductId(),
                    reduceKey,
                    new StockUpdateRequest(request.getQuantity()));

            savedOrder.setStatus(OrderStatus.CONFIRMED);

            Order confirmedOrder = orderPersistenceService.saveOrder(savedOrder);

            return toResponse(confirmedOrder);

        } catch (RuntimeException stockException) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock operation outcome is uncertain. "
                            + "Order requires reconciliation.",
                    stockException);
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
    

	@Override
	public OrderResponse cancelOrder(Long orderId) {
	
	    if (orderId == null || orderId <= 0) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST,
	                "A valid order ID is required"
	        );
	    }
	
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new ResponseStatusException(
	                    HttpStatus.NOT_FOUND,
	                    "Order not found: " + orderId
	            ));
	
	    // Repeated cancellation must not restore stock again.
	    if (order.getStatus() == OrderStatus.CANCELLED) {
	        return toResponse(order);
	    }
	
	    // Only confirmed orders can be cancelled.
	    if (order.getStatus() != OrderStatus.CONFIRMED) {
	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT,
	                "Only confirmed orders can be cancelled"
	        );
	    }
	
	    // Stable key: retries for this order reuse the same key.
	    String restoreKey = "order-" + order.getId() + "-restore";
	
	    try {
	        productClient.restoreStock(
	                order.getProductId(),
	                restoreKey,
	                new StockUpdateRequest(order.getQuantity())
	        );
	    } catch (RuntimeException exception) {
	        throw new ResponseStatusException(
	                HttpStatus.SERVICE_UNAVAILABLE,
	                "Stock restoration outcome is uncertain. "
	                        + "Retry cancellation using the same order ID.",
	                exception
	        );
	    }
	
	    // Mark cancelled only after the restore call succeeds.
	    order.setStatus(OrderStatus.CANCELLED);
	
	    try {
	        Order cancelledOrder =
	                orderPersistenceService.saveOrder(order);
	
	        return toResponse(cancelledOrder);
	
	    } catch (RuntimeException exception) {
	        // Stock may already have been restored.
	        // A retry must reuse the same restoreKey.
	        throw new ResponseStatusException(
	                HttpStatus.SERVICE_UNAVAILABLE,
	                "Stock may have been restored, but cancellation "
	                        + "could not be saved. Retry the cancellation.",
	                exception
	        );
	    }
	}

}
