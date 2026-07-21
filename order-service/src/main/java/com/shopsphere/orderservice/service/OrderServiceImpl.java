package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.client.ProductClient;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.dto.ProductResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) {

        // Fetch product from Product Service
        ProductResponse product =
                productClient.getProductById(request.getProductId());

        // Check stock
        if (product.getStock() < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock");
        }

        // Calculate total price
        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);

        Order savedOrder = orderRepository.save(order);

        // Build response
        OrderResponse response = new OrderResponse();
        response.setId(savedOrder.getId());
        response.setUserId(savedOrder.getUserId());
        response.setProductId(savedOrder.getProductId());
        response.setQuantity(savedOrder.getQuantity());
        response.setTotalPrice(savedOrder.getTotalPrice());
        response.setStatus(savedOrder.getStatus());
        response.setOrderDate(savedOrder.getOrderDate());

        return response;
    }
}