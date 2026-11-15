package com.shopsphere.orderservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.OrderStatusHistory;

class OrderMapperTest {

    @Test
    void toResponse_mapsOrderWithItemsAndAddress() {
        Order order = new Order();
        order.setId(42L);
        order.setUserId(7L);
        order.setProductId(100L);
        order.setQuantity(2);
        order.setCurrency("INR");
        order.setSubtotalAmount(new BigDecimal("200.00"));
        order.setShippingAmount(new BigDecimal("20.00"));
        order.setTaxAmount(new BigDecimal("18.00"));
        order.setDiscountAmount(new BigDecimal("10.00"));
        order.setTotalPrice(new BigDecimal("228.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCancellationReason(null);
        order.setContactEmail("buyer@example.com");
        order.setOrderDate(LocalDateTime.of(2026, 3, 10, 9, 30));
        order.setShippingAddress(new Address("Alice", "Line 1", null, "Mumbai", "MH", "400001", "IN", "9999999999"));
        order.addItem(new OrderItem(100L, "Mouse", new BigDecimal("100.00"), 2));

        OrderResponse res = OrderMapper.toResponse(order);

        assertThat(res.id()).isEqualTo(42L);
        assertThat(res.userId()).isEqualTo(7L);
        assertThat(res.totalPrice()).isEqualByComparingTo("228.00");
        assertThat(res.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).productName()).isEqualTo("Mouse");
        assertThat(res.items().get(0).lineTotal()).isEqualByComparingTo("200.00");
        assertThat(res.shippingAddress()).isNotNull();
        assertThat(res.shippingAddress().city()).isEqualTo("Mumbai");
    }

    @Test
    void toResponse_handlesNullAddressAndMapsHistory() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(OrderStatus.PENDING);

        OrderResponse res = OrderMapper.toResponse(order);
        assertThat(res.shippingAddress()).isNull();
        assertThat(res.items()).isEmpty();

        LocalDateTime ts = LocalDateTime.of(2026, 4, 1, 8, 0);
        OrderStatusHistory hist = new OrderStatusHistory(1L, OrderStatus.PENDING, OrderStatus.FAILED, "No stock", "system:inventory", ts);
        StatusHistoryResponse histRes = OrderMapper.toResponse(hist);

        assertThat(histRes.fromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(histRes.toStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(histRes.reason()).isEqualTo("No stock");
        assertThat(histRes.actor()).isEqualTo("system:inventory");
        assertThat(histRes.createdAt()).isEqualTo(ts);
    }
}
