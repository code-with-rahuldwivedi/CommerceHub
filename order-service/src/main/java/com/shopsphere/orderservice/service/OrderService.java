package com.shopsphere.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.shopsphere.orderservice.client.CatalogGateway;
import com.shopsphere.orderservice.client.CatalogProduct;
import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.dto.AddressRequest;
import com.shopsphere.orderservice.dto.OrderItemRequest;
import com.shopsphere.orderservice.dto.OrderMapper;
import com.shopsphere.orderservice.dto.OrderRequest;
import com.shopsphere.orderservice.dto.OrderResponse;
import com.shopsphere.orderservice.entity.Address;
import com.shopsphere.orderservice.entity.Order;
import com.shopsphere.orderservice.entity.OrderItem;
import com.shopsphere.orderservice.exception.IdempotencyConflictException;
import com.shopsphere.orderservice.exception.InvalidOrderRequestException;
import com.shopsphere.orderservice.exception.OrderNotFoundException;
import com.shopsphere.orderservice.exception.ProductUnavailableException;
import com.shopsphere.orderservice.metrics.OrderMetrics;
import com.shopsphere.orderservice.repository.OrderRepository;
import com.shopsphere.orderservice.security.CurrentUser;

/**
 * Commands coming from the API. Deliberately NOT @Transactional: the call to product-service must never hold
 * a database connection or transaction open; each database step is its own short transaction in
 * {@link OrderLifecycleService}.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:\\-]{1,100}$");

    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final CatalogGateway catalog;
    private final PricingPolicy pricingPolicy;
    private final OrderProperties properties;
    private final OrderMetrics metrics;

    public OrderService(OrderRepository orders, OrderLifecycleService lifecycle, CatalogGateway catalog,
                        PricingPolicy pricingPolicy, OrderProperties properties, OrderMetrics metrics) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.catalog = catalog;
        this.pricingPolicy = pricingPolicy;
        this.properties = properties;
        this.metrics = metrics;
    }

    // ------------------------------------------------------------------ place

    public PlacementResult place(long userId, OrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidOrderRequestException(
                    "Idempotency-Key is required (1-100 characters: letters, digits, . _ : -)");
        }
        List<OrderLine> lines = normalize(request);
        if (properties.requireShippingAddress() && request.shippingAddress() == null) {
            throw new InvalidOrderRequestException("A shipping address is required");
        }
        String contactEmail = request.contactEmail() == null || request.contactEmail().isBlank()
                ? null : request.contactEmail().trim().toLowerCase(Locale.ROOT);
        String fingerprint = RequestFingerprint.of(lines, request.shippingAddress(), contactEmail);

        // A retry of an order that already exists must not call the catalog again, nor create a second order.
        Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), lines, fingerprint);
        }

        Map<Long, CatalogProduct> products = catalog.findActiveProducts(lines.stream().map(OrderLine::productId).toList());
        List<Long> unavailable = lines.stream()
                .map(OrderLine::productId)
                .filter(id -> !isSellable(products.get(id)))
                .toList();
        if (!unavailable.isEmpty()) {
            throw new ProductUnavailableException(unavailable);
        }

        Order order = buildOrder(userId, idempotencyKey, fingerprint, contactEmail, request.shippingAddress(), lines, products);
        try {
            Order saved = lifecycle.place(order, "user:" + userId);
            metrics.orderPlaced("created");
            log.info("Order {} placed by user {} ({} line(s), total {} {})", saved.getId(), userId,
                    lines.size(), saved.getTotalPrice(), saved.getCurrency());
            return new PlacementResult(OrderMapper.toResponse(saved), false);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            // Two requests with the same key raced: the other one won (MySQL reports that as a duplicate key, or as a
            // deadlock for the losers when several race at once). Return ITS order, or fail if the same key was used
            // for a different request. If there is no such order the error had another cause: do not hide it.
            Order winner = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> e);
            return replay(winner, lines, fingerprint);
        }
    }

    private PlacementResult replay(Order existing, List<OrderLine> lines, String fingerprint) {
        boolean same = existing.getRequestHash() != null
                ? existing.getRequestHash().equals(fingerprint)
                : matchesLegacyOrder(existing, lines);
        if (!same) {
            throw new IdempotencyConflictException();
        }
        metrics.orderPlaced("replayed");
        return new PlacementResult(OrderMapper.toResponse(existing), true);
    }

    /** Orders created before request fingerprints existed: compare the single line. */
    private static boolean matchesLegacyOrder(Order existing, List<OrderLine> lines) {
        return lines.size() == 1
                && existing.getItems().size() == 1
                && existing.getItems().get(0).getProductId().equals(lines.get(0).productId())
                && existing.getItems().get(0).getQuantity() == lines.get(0).quantity();
    }

    private Order buildOrder(long userId, String idempotencyKey, String fingerprint, String contactEmail,
                             AddressRequest address, List<OrderLine> lines, Map<Long, CatalogProduct> products) {
        Order order = new Order();
        order.setUserId(userId);
        order.setCurrency(properties.currency());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(fingerprint);
        order.setContactEmail(contactEmail);
        order.setShippingAddress(toAddress(address));

        List<PricingPolicy.PricingLine> pricingLines = new ArrayList<>();
        for (OrderLine line : lines) {
            CatalogProduct product = products.get(line.productId());
            BigDecimal unitPrice = product.price().setScale(2, RoundingMode.HALF_UP);
            order.addItem(new OrderItem(line.productId(), truncate(product.name(), 150), unitPrice, line.quantity()));
            pricingLines.add(new PricingPolicy.PricingLine(line.productId(), line.quantity(), unitPrice));
        }

        PricingPolicy.PriceBreakdown price = pricingPolicy.price(
                new PricingPolicy.PricingContext(userId, properties.currency(), pricingLines, address));
        if (price.total().signum() <= 0) {
            throw new InvalidOrderRequestException("The order total must be greater than zero");
        }
        order.setSubtotalAmount(price.subtotal());
        order.setShippingAmount(price.shipping());
        order.setTaxAmount(price.tax());
        order.setDiscountAmount(price.discount());
        order.setTotalPrice(price.total());

        if (lines.size() == 1) { // keep the legacy single-item columns filled
            order.setProductId(lines.get(0).productId());
            order.setQuantity(lines.get(0).quantity());
        }
        return order;
    }

    /** Merges duplicate products, enforces limits and returns the lines sorted by product id. */
    List<OrderLine> normalize(OrderRequest request) {
        List<OrderItemRequest> raw = new ArrayList<>();
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        boolean hasLegacy = request.productId() != null || request.quantity() != null;

        if (hasItems && hasLegacy) {
            throw new InvalidOrderRequestException("Use either items or productId/quantity, not both");
        }
        if (hasItems) {
            raw.addAll(request.items());
        } else if (request.productId() != null && request.quantity() != null) {
            raw.add(new OrderItemRequest(request.productId(), request.quantity()));
        } else {
            throw new InvalidOrderRequestException("An order needs at least one item");
        }

        Map<Long, Integer> merged = new TreeMap<>();
        for (OrderItemRequest item : raw) {
            try {
                merged.merge(item.productId(), item.quantity(), (a, b) -> Math.addExact(a, b));
            } catch (ArithmeticException e) {
                throw new InvalidOrderRequestException("Quantity is too large");
            }
        }
        if (merged.size() > properties.maxItems()) {
            throw new InvalidOrderRequestException(
                    "An order can contain at most " + properties.maxItems() + " different product(s)");
        }
        List<OrderLine> lines = new ArrayList<>();
        merged.forEach((productId, quantity) -> {
            if (quantity > properties.maxQuantityPerItem()) {
                throw new InvalidOrderRequestException(
                        "At most " + properties.maxQuantityPerItem() + " units of one product per order");
            }
            lines.add(new OrderLine(productId, quantity));
        });
        return lines;
    }

    // ------------------------------------------------------------------ cancel / fulfilment

    public OrderResponse cancel(long orderId, CurrentUser caller, String reason) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(OrderNotFoundException::new);
        requireOwnerOrAdmin(order, caller);
        return OrderMapper.toResponse(lifecycle.requestCancellation(orderId, reason, actor(caller)));
    }

    public OrderResponse markShipped(long orderId, CurrentUser admin) {
        return OrderMapper.toResponse(lifecycle.markShipped(orderId, actor(admin)));
    }

    public OrderResponse markDelivered(long orderId, CurrentUser admin) {
        return OrderMapper.toResponse(lifecycle.markDelivered(orderId, actor(admin)));
    }

    // ------------------------------------------------------------------ helpers

    /** Somebody else's order is reported as "not found": its existence is not revealed. */
    static void requireOwnerOrAdmin(Order order, CurrentUser caller) {
        if (!caller.admin() && !order.getUserId().equals(caller.userId())) {
            throw new OrderNotFoundException();
        }
    }

    static String actor(CurrentUser caller) {
        return (caller.admin() ? "admin:" : "user:") + caller.userId();
    }

    private static boolean isSellable(CatalogProduct product) {
        return product != null && product.price() != null && product.price().signum() > 0;
    }

    private static Address toAddress(AddressRequest a) {
        if (a == null) {
            return null;
        }
        return new Address(clean(a.fullName()), clean(a.line1()), clean(a.line2()), clean(a.city()),
                clean(a.state()), clean(a.postalCode()), clean(a.country()), clean(a.phone()));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
