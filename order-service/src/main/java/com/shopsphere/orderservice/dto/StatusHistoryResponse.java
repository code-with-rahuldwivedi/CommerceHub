package com.shopsphere.orderservice.dto;

import java.time.LocalDateTime;

import com.shopsphere.orderservice.entity.OrderStatus;

public record StatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, String reason,
                                    String actor, LocalDateTime createdAt) {
}
