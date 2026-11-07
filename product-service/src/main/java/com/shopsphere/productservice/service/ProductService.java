package com.shopsphere.productservice.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.productservice.dto.ProductQuery;
import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;
import com.shopsphere.productservice.exception.ConflictException;
import com.shopsphere.productservice.exception.InvalidRequestException;
import com.shopsphere.productservice.exception.ProductNotFoundException;
import com.shopsphere.productservice.repository.ProductRepository;
import com.shopsphere.productservice.repository.ProductSpecifications;
import com.shopsphere.productservice.security.CurrentUser;

/**
 * Catalog rules:
 * - anonymous users and customers only ever see ACTIVE products;
 * - admins also see INACTIVE (and ARCHIVED when asked for);
 * - "delete" is a soft delete (ARCHIVED): orders keep referencing the product id;
 * - every admin mutation is written to the AUDIT log with the acting admin.
 */
@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_BATCH_SIZE = 100;
    public static final int LEGACY_LIST_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "id",
            "name", "name",
            "price", "price",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }


    @Transactional(readOnly = true)
    public Page<ProductResponse> search(ProductQuery query, CurrentUser viewer) {
        if (query.minPrice() != null && query.maxPrice() != null
                && query.minPrice().compareTo(query.maxPrice()) > 0) {
            throw new InvalidRequestException("minPrice must not be greater than maxPrice");
        }
        Pageable pageable = PageRequest.of(
                query.page(), Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE), parseSort(query.sort()));

        // Customers can only ever see ACTIVE products, whatever they ask for.
        if (!viewer.admin() && query.status() != null && query.status() != ProductStatus.ACTIVE) {
            return Page.empty(pageable);
        }
        Set<ProductStatus> visible = visibleStatuses(query.status(), viewer);

        return products.findAll(
                ProductSpecifications.matching(visible, query.category(), query.minPrice(), query.maxPrice(),
                        query.text()),
                pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id, CurrentUser viewer) {
        Product product = find(id);
        if (!viewer.admin() && product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotFoundException(); // do not reveal that an unpublished product exists
        }
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getBatch(Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new InvalidRequestException("At most " + MAX_BATCH_SIZE + " ids per request");
        }
        return products.findAllByIdInAndStatus(distinct, ProductStatus.ACTIVE)
                .stream().map(ProductResponse::from).toList();
    }

    /** For the deprecated GET /api/getAllProducts: first {@value #LEGACY_LIST_LIMIT} products by id. */
    @Transactional(readOnly = true)
    public List<ProductResponse> legacyList(CurrentUser viewer) {
        Page<ProductResponse> page = search(
                new ProductQuery(null, null, null, null, null, 0, LEGACY_LIST_LIMIT, "id,asc"), viewer);
        if (page.getTotalElements() > LEGACY_LIST_LIMIT) {
            log.warn("Deprecated unpaged product list truncated to {} of {} products: migrate the caller to GET /api/products",
                    LEGACY_LIST_LIMIT, page.getTotalElements());
        }
        return page.getContent();
    }


    @Transactional
    public ProductResponse create(ProductRequest request, CurrentUser actor) {
        Product product = new Product();
        apply(product, request);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedBy(actor.userId());
        product.setUpdatedBy(actor.userId());

        Product saved = products.saveAndFlush(product);
        AUDIT.info("product_created productId={} actor={} price={}", saved.getId(), actor.userId(), saved.getPrice());
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(long id, ProductRequest request, CurrentUser actor) {
        Product product = find(id);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new ConflictException("Archived products cannot be edited, restore the product first");
        }
        if (request.version() != null && !request.version().equals(product.getVersion())) {
            throw new ConflictException("The product was modified by someone else, reload it and retry");
        }

        BigDecimal oldPrice = product.getPrice();
        apply(product, request);
        product.setUpdatedBy(actor.userId());

        Product saved = products.saveAndFlush(product);
        if (oldPrice.compareTo(saved.getPrice()) != 0) {
            AUDIT.info("product_price_changed productId={} actor={} oldPrice={} newPrice={}",
                    id, actor.userId(), oldPrice, saved.getPrice());
        }
        AUDIT.info("product_updated productId={} actor={}", id, actor.userId());
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse changeStatus(long id, ProductStatus newStatus, CurrentUser actor) {
        Product product = find(id);
        if (product.getStatus() == newStatus) {
            return ProductResponse.from(product);
        }
        ProductStatus oldStatus = product.getStatus();
        product.setStatus(newStatus);
        product.setUpdatedBy(actor.userId());

        Product saved = products.saveAndFlush(product);
        AUDIT.info("product_status_changed productId={} actor={} {} -> {}", id, actor.userId(), oldStatus, newStatus);
        return ProductResponse.from(saved);
    }

    /** Soft delete. Idempotent: archiving an archived product is a no-op. */
    @Transactional
    public void archive(long id, CurrentUser actor) {
        changeStatus(id, ProductStatus.ARCHIVED, actor);
    }

    private Product find(long id) {
        return products.findById(id).orElseThrow(ProductNotFoundException::new);
    }

    private static void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setImageUrl(normalizeImageUrl(request.imageUrl()));
    }

    private static String normalizeImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }

    private static Set<ProductStatus> visibleStatuses(ProductStatus requested, CurrentUser viewer) {
        if (!viewer.admin()) {
            return EnumSet.of(ProductStatus.ACTIVE);
        }
        return requested != null
                ? EnumSet.of(requested)
                : EnumSet.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE); // archived only on request
    }

    /** Whitelist of sortable fields: callers can never sort by arbitrary columns. "id" is the tie-breaker. */
    static Sort parseSort(String raw) {
        String value = raw == null || raw.isBlank() ? DEFAULT_SORT : raw.trim();
        String[] parts = value.split(",");
        if (parts.length > 2) {
            throw invalidSort();
        }
        String property = SORTABLE_FIELDS.get(parts[0].trim());
        if (property == null) {
            throw invalidSort();
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String requested = parts[1].trim();
            if ("desc".equalsIgnoreCase(requested)) {
                direction = Sort.Direction.DESC;
            } else if (!"asc".equalsIgnoreCase(requested)) {
                throw invalidSort();
            }
        }
        Sort sort = Sort.by(new Sort.Order(direction, property));
        return "id".equals(property) ? sort : sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static InvalidRequestException invalidSort() {
        return new InvalidRequestException(
                "Invalid sort, use <field>[,asc|desc] with field one of: id, name, price, createdAt, updatedAt");
    }
}
