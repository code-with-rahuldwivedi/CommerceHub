package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;

public interface OrderService {

    OrderResponse placeOrder(OrderRequest request);
}