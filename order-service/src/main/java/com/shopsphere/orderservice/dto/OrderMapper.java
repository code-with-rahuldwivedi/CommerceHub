package com.shopsphere.orderservice.dto;

import java.util.List;

import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatusHistory;

public final class OrderMapper {

    private OrderMapper() {
    }

    /** The order's lines must be loaded (use the repository methods with the "items" entity graph). */
    public static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                items,
                order.getCurrency(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCancellationReason(),
                toResponse(order.getShippingAddress()),
                order.getContactEmail(),
                order.getOrderDate(),
                order.getUpdatedAt(),
                order.getVersion());
    }

    public static StatusHistoryResponse toResponse(OrderStatusHistory history) {
        return new StatusHistoryResponse(history.getFromStatus(), history.getToStatus(), history.getReason(),
                history.getActor(), history.getCreatedAt());
    }

    private static AddressResponse toResponse(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(address.getFullName(), address.getLine1(), address.getLine2(), address.getCity(),
                address.getState(), address.getPostalCode(), address.getCountry(), address.getPhone());
    }
}
