package com.shopsphere.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.shopsphere.orderservice.client.CatalogGateway;
import com.shopsphere.orderservice.client.CatalogProduct;
import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.dto.AddressRequest;
import com.shopsphere.orderservice.dto.OrderItemRequest;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.entity.OrderStatus;
import com.shopsphere.orderservice.exception.IdempotencyConflictException;
import com.shopsphere.orderservice.exception.InvalidOrderRequestException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.ProductUnavailableException;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.security.CurrentUser;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orders;
    @Mock private OrderLifecycleService lifecycle;
    @Mock private CatalogGateway catalog;
    @Mock private OrderMetrics metrics;

    private final PricingPolicy pricingPolicy = new DefaultPricingPolicy();
    private OrderProperties properties;
    private OrderService service;

    @BeforeEach
    void setUp() {
        properties = defaultProperties(5, 10, false);
        service = new OrderService(orders, lifecycle, catalog, pricingPolicy, properties, metrics);
    }

    private static OrderProperties defaultProperties(int maxItems, int maxQty, boolean requireAddress) {
        return new OrderProperties(
                "INR", maxItems, maxQty, requireAddress,
                new OrderProperties.Events("order.events.v1", true, "order-events"),
                new OrderProperties.Payment(false),
                new OrderProperties.Expiry(false, Duration.ofMinutes(15), Duration.ofMinutes(30), 60000),
                new OrderProperties.Inbound(false, "inventory.events.v1", "inventory-events",
                        new OrderProperties.Inbound.Retry(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10))),
                new OrderProperties.Catalog(3, Duration.ofMillis(200), 10, 50, Duration.ofSeconds(10))
        );
    }

    @Test
    void place_newSingleItemOrder_succeedsAndPopulatesLegacyFields() {
        OrderRequest request = new OrderRequest(101L, 2, null,
                new AddressRequest("John", "Street 1", null, "Bengaluru", "KA", "560001", "IN", "9876543210"),
                "John@Example.COM ");

        when(orders.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
        when(catalog.findActiveProducts(List.of(101L))).thenReturn(
                Map.of(101L, new CatalogProduct(101L, "Mechanical Keyboard", new BigDecimal("2500.00"), "ACTIVE")));
        when(lifecycle.place(any(Order.class), eq("user:7"))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(500L);
            o.setStatus(OrderStatus.PENDING);
            o.setOrderDate(LocalDateTime.of(2026, 5, 1, 10, 0));
            return o;
        });

        PlacementResult result = service.place(7L, request, "key-1");

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().id()).isEqualTo(500L);
        assertThat(result.order().productId()).isEqualTo(101L);
        assertThat(result.order().quantity()).isEqualTo(2);
        assertThat(result.order().totalPrice()).isEqualByComparingTo("5000.00");
        assertThat(result.order().contactEmail()).isEqualTo("john@example.com");
        verify(metrics).orderPlaced("created");
    }

    @Test
    void place_mergesDuplicateItemsAndSortsByProductId() {
        OrderRequest request = new OrderRequest(null, null,
                List.of(new OrderItemRequest(20L, 1), new OrderItemRequest(10L, 2), new OrderItemRequest(20L, 3)),
                null, null);

        when(orders.findByUserIdAndIdempotencyKey(1L, "idem-multi")).thenReturn(Optional.empty());
        when(catalog.findActiveProducts(List.of(10L, 20L))).thenReturn(Map.of(
                10L, new CatalogProduct(10L, "Item 10", new BigDecimal("100.00"), "ACTIVE"),
                20L, new CatalogProduct(20L, "Item 20", new BigDecimal("50.00"), "ACTIVE")
        ));
        when(lifecycle.place(any(Order.class), eq("user:1"))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(12L);
            return o;
        });

        PlacementResult result = service.place(1L, request, "idem-multi");

        assertThat(result.order().productId()).isNull();
        assertThat(result.order().quantity()).isNull();
        assertThat(result.order().items()).hasSize(2);
        assertThat(result.order().totalPrice()).isEqualByComparingTo("400.00");
    }

    @Test
    void place_replaysExistingOrderWhenFingerprintMatches() {
        OrderRequest request = new OrderRequest(101L, 2, null, null, null);
        String fingerprint = RequestFingerprint.of(List.of(new OrderLine(101L, 2)), null, null);

        Order existing = new Order();
        existing.setId(88L);
        existing.setUserId(7L);
        existing.setRequestHash(fingerprint);
        existing.setStatus(OrderStatus.CONFIRMED);
        existing.addItem(new OrderItem(101L, "Mouse", new BigDecimal("500.00"), 2));

        when(orders.findByUserIdAndIdempotencyKey(7L, "retry-key")).thenReturn(Optional.of(existing));

        PlacementResult result = service.place(7L, request, "retry-key");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(88L);
        verifyNoInteractions(catalog, lifecycle);
        verify(metrics).orderPlaced("replayed");
    }

    @Test
    void place_replaysLegacyOrderWithoutFingerprint_andThrowsConflictOnMismatch() {
        Order existing = new Order();
        existing.setId(88L);
        existing.setUserId(7L);
        existing.setRequestHash(null);
        existing.addItem(new OrderItem(101L, "Mouse", new BigDecimal("500.00"), 2));

        when(orders.findByUserIdAndIdempotencyKey(7L, "legacy-key")).thenReturn(Optional.of(existing));

        PlacementResult replayed = service.place(7L, new OrderRequest(101L, 2, null, null, null), "legacy-key");
        assertThat(replayed.replayed()).isTrue();

        assertThatThrownBy(() -> service.place(7L, new OrderRequest(101L, 3, null, null, null), "legacy-key"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void place_handlesConcurrentInsertRaceByReplayingWinner() {
        OrderRequest request = new OrderRequest(101L, 1, null, null, null);
        String fingerprint = RequestFingerprint.of(List.of(new OrderLine(101L, 1)), null, null);

        Order winner = new Order();
        winner.setId(99L);
        winner.setUserId(7L);
        winner.setRequestHash(fingerprint);

        when(orders.findByUserIdAndIdempotencyKey(7L, "race-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(catalog.findActiveProducts(List.of(101L)))
                .thenReturn(Map.of(101L, new CatalogProduct(101L, "Pen", new BigDecimal("10.00"), "ACTIVE")));
        when(lifecycle.place(any(), any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        PlacementResult result = service.place(7L, request, "race-key");

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(99L);
    }

    @Test
    void place_throwsProductUnavailableWhenMissingOrZeroPriced() {
        OrderRequest request = new OrderRequest(null, null,
                List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 1)), null, null);

        when(orders.findByUserIdAndIdempotencyKey(1L, "k1")).thenReturn(Optional.empty());
        when(catalog.findActiveProducts(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, new CatalogProduct(1L, "Freebie", BigDecimal.ZERO, "ACTIVE")));

        assertThatThrownBy(() -> service.place(1L, request, "k1"))
                .isInstanceOf(ProductUnavailableException.class)
                .satisfies(ex -> assertThat(((ProductUnavailableException) ex).getProductIds()).containsExactly(1L, 2L));
    }

    @Test
    void place_validatesIdempotencyKeyItemsAndShippingAddress() {
        OrderRequest validReq = new OrderRequest(1L, 1, null, null, null);
        assertThatThrownBy(() -> service.place(1L, validReq, null))
                .isInstanceOf(InvalidOrderRequestException.class);
        assertThatThrownBy(() -> service.place(1L, validReq, "bad key with spaces"))
                .isInstanceOf(InvalidOrderRequestException.class);

        OrderRequest both = new OrderRequest(1L, 1, List.of(new OrderItemRequest(1L, 1)), null, null);
        assertThatThrownBy(() -> service.place(1L, both, "ok-key"))
                .isInstanceOf(InvalidOrderRequestException.class);

        OrderRequest empty = new OrderRequest(null, null, List.of(), null, null);
        assertThatThrownBy(() -> service.place(1L, empty, "ok-key"))
                .isInstanceOf(InvalidOrderRequestException.class);

        OrderRequest tooManyUnits = new OrderRequest(1L, 11, null, null, null);
        assertThatThrownBy(() -> service.place(1L, tooManyUnits, "ok-key"))
                .isInstanceOf(InvalidOrderRequestException.class);

        OrderService strictService = new OrderService(orders, lifecycle, catalog, pricingPolicy,
                defaultProperties(1, 10, true), metrics);
        OrderRequest twoItems = new OrderRequest(null, null,
                List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 1)), null, null);
        assertThatThrownBy(() -> strictService.place(1L, twoItems, "ok-key"))
                .isInstanceOf(InvalidOrderRequestException.class);

        assertThatThrownBy(() -> strictService.place(1L, validReq, "ok-key"))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("shipping address");
    }

    @Test
    void place_rejectsNonPositiveOrderTotal() {
        PricingPolicy zeroPolicy = ctx -> new PricingPolicy.PriceBreakdown(
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"));
        OrderService zeroTotalService = new OrderService(orders, lifecycle, catalog, zeroPolicy, properties, metrics);

        when(orders.findByUserIdAndIdempotencyKey(1L, "k1")).thenReturn(Optional.empty());
        when(catalog.findActiveProducts(List.of(1L)))
                .thenReturn(Map.of(1L, new CatalogProduct(1L, "Item", new BigDecimal("10.00"), "ACTIVE")));

        assertThatThrownBy(() -> zeroTotalService.place(1L, new OrderRequest(1L, 1, null, null, null), "k1"))
                .isInstanceOf(InvalidOrderRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void cancel_and_fulfilment_enforceOwnershipAndDelegateToLifecycle() {
        Order order = new Order();
        order.setId(55L);
        order.setUserId(10L);
        order.setStatus(OrderStatus.CANCEL_REQUESTED);

        when(orders.findWithItemsById(55L)).thenReturn(Optional.of(order));
        when(lifecycle.requestCancellation(55L, "Changed mind", "user:10")).thenReturn(order);

        assertThatThrownBy(() -> service.cancel(55L, new CurrentUser(99L, false), "Changed mind"))
                .isInstanceOf(OrderNotFoundException.class);

        OrderResponse cancelled = service.cancel(55L, new CurrentUser(10L, false), "Changed mind");
        assertThat(cancelled.id()).isEqualTo(55L);

        when(lifecycle.requestCancellation(55L, "Fraud", "admin:1")).thenReturn(order);
        service.cancel(55L, new CurrentUser(1L, true), "Fraud");

        when(lifecycle.markShipped(55L, "admin:1")).thenReturn(order);
        when(lifecycle.markDelivered(55L, "admin:1")).thenReturn(order);
        assertThat(service.markShipped(55L, new CurrentUser(1L, true)).id()).isEqualTo(55L);
        assertThat(service.markDelivered(55L, new CurrentUser(1L, true)).id()).isEqualTo(55L);
    }
}
