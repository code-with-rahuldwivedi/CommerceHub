package com.shopsphere.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.entity.OrderStatusHistory;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.repository.OrderStatusHistoryRepository;
import com.shopsphere.orderservice.security.CurrentUser;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock private OrderRepository orders;
    @Mock private OrderStatusHistoryRepository history;

    @InjectMocks private OrderQueryService queryService;

    @Test
    void getAndHistory_enforceVisibilityAndMapDtos() {
        Order order = new Order();
        order.setId(10L);
        order.setUserId(5L);
        order.setStatus(OrderStatus.CONFIRMED);

        when(orders.findWithItemsById(10L)).thenReturn(Optional.of(order));
        when(history.findByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(
                new OrderStatusHistory(10L, null, OrderStatus.PENDING, null, "user:5", LocalDateTime.now())
        ));

        assertThatThrownBy(() -> queryService.get(10L, new CurrentUser(99L, false)))
                .isInstanceOf(OrderNotFoundException.class);

        assertThat(queryService.get(10L, new CurrentUser(5L, false)).id()).isEqualTo(10L);
        assertThat(queryService.history(10L, new CurrentUser(1L, true))).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMineAndListAll_clampsPageAndSizeBounds() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(5L);
        order.setStatus(OrderStatus.PENDING);

        when(orders.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        var page = queryService.listMine(5L, OrderStatus.PENDING, -2, 500);
        assertThat(page.getContent()).hasSize(1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(OrderQueryService.MAX_PAGE_SIZE);

        queryService.listAll(null, null, 1, 0);
    }
}
