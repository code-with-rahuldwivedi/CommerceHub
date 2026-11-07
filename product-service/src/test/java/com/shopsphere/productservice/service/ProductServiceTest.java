package com.shopsphere.productservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.shopsphere.productservice.dto.ProductQuery;
import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;
import com.shopsphere.productservice.exception.InvalidRequestException;
import com.shopsphere.productservice.repository.ProductRepository;
import com.shopsphere.productservice.security.CurrentUser;

@ExtendWith(MockitoExtension.class)
class ProductServiceCoverageTest {

    private static final CurrentUser ADMIN =
            new CurrentUser(1L, true);

    private static final CurrentUser CUSTOMER =
            new CurrentUser(2L, false);

    private static final CurrentUser ANONYMOUS =
            CurrentUser.anonymous();

    @Mock
    private ProductRepository products;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products);
    }

    private Product product(long id, ProductStatus status) {

        Product product = new Product();

        product.setId(id);
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setPrice(new BigDecimal("100.00"));
        product.setCategory(Category.MOBILES);
        product.setStatus(status);

        return product;
    }

    private ProductRequest request() {

        return new ProductRequest(
                "Updated Product",
                "Updated Description",
                new BigDecimal("200.00"),
                Category.MOBILES,
                "https://cdn.example.com/image.png",
                null
        );
    }

    @Test
    void getBatch_exactlyMaximumAllowedIds_shouldSucceed() {

        List<Long> ids = LongStream
                .rangeClosed(1, ProductService.MAX_BATCH_SIZE)
                .boxed()
                .toList();

        when(products.findAllByIdInAndStatus(
                anyCollection(),
                eq(ProductStatus.ACTIVE)
        )).thenReturn(List.of());

        var result = service.getBatch(ids);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(products).findAllByIdInAndStatus(
                anyCollection(),
                eq(ProductStatus.ACTIVE)
        );
    }

    @Test
    void search_onlyMinimumPrice_shouldSucceed() {

        ProductQuery query = new ProductQuery(
                null,
                null,
                new BigDecimal("50"),
                null,
                null,
                0,
                20,
                null
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<?> result = service.search(query, CUSTOMER);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }


    @Test
    void search_onlyMaximumPrice_shouldSucceed() {

        ProductQuery query = new ProductQuery(
                null,
                null,
                null,
                new BigDecimal("500"),
                null,
                0,
                20,
                null
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<?> result = service.search(query, CUSTOMER);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }

    // --------------------------------------------------
    // 4. SEARCH - MINIMUM AND MAXIMUM PRICE EQUAL
    // --------------------------------------------------

    @Test
    void search_equalMinimumAndMaximumPrice_shouldSucceed() {

        BigDecimal price = new BigDecimal("100");

        ProductQuery query = new ProductQuery(
                null,
                null,
                price,
                price,
                null,
                0,
                20,
                null
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<?> result = service.search(query, ADMIN);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }

    // --------------------------------------------------
    // 5. CUSTOMER REQUESTS ACTIVE STATUS
    // --------------------------------------------------

    @Test
    void search_customerRequestsActiveStatus_shouldQueryDatabase() {

        ProductQuery query = new ProductQuery(
                null,
                null,
                null,
                null,
                ProductStatus.ACTIVE,
                0,
                20,
                null
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(product(1L, ProductStatus.ACTIVE))
        ));

        Page<?> result = service.search(query, CUSTOMER);

        assertEquals(1, result.getTotalElements());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }

    // --------------------------------------------------
    // 6. PARSE SORT - EXPLICIT ASCENDING DIRECTION
    // --------------------------------------------------

    @Test
    void parseSort_explicitAscendingDirection_shouldSucceed() {

        Sort sort = ProductService.parseSort("price,asc");

        assertNotNull(sort);

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("price").getDirection()
        );

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("id").getDirection()
        );
    }

    // --------------------------------------------------
    // 7. PARSE SORT - WHITESPACE HANDLING
    // --------------------------------------------------

    @Test
    void parseSort_withWhitespace_shouldNormalizeInput() {

        Sort sort = ProductService.parseSort(" name , asc ");

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("name").getDirection()
        );

        assertEquals(
                Sort.Direction.ASC,
                sort.getOrderFor("id").getDirection()
        );
    }

    // --------------------------------------------------
    // 8. LEGACY LIST - EXACTLY 500 PRODUCTS
    // --------------------------------------------------

    @Test
    void legacyList_exactly500Products_shouldReturnContent() {

        Page<Product> page = new PageImpl<>(
                List.of(product(1L, ProductStatus.ACTIVE)),
                PageRequest.of(0, ProductService.LEGACY_LIST_LIMIT),
                ProductService.LEGACY_LIST_LIMIT
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(page);

        var result = service.legacyList(ANONYMOUS);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }

    // --------------------------------------------------
    // 9. LEGACY LIST - MORE THAN 500 PRODUCTS
    // --------------------------------------------------

    @Test
    void legacyList_moreThan500Products_shouldReturnLimitedContent() {

        Page<Product> page = new PageImpl<>(
                List.of(product(2L, ProductStatus.ACTIVE)),
                PageRequest.of(0, ProductService.LEGACY_LIST_LIMIT),
                ProductService.LEGACY_LIST_LIMIT + 1L
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(page);

        var result = service.legacyList(ANONYMOUS);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
    }

    // --------------------------------------------------
    // 10. LEGACY LIST - ADMIN VIEWER
    // --------------------------------------------------

    @Test
    void legacyList_adminViewer_shouldReturnProducts() {

        Page<Product> page = new PageImpl<>(
                List.of(
                        product(3L, ProductStatus.ACTIVE),
                        product(4L, ProductStatus.INACTIVE)
                )
        );

        when(products.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(page);

        var result = service.legacyList(ADMIN);

        assertEquals(2, result.size());

        verify(products).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Product>>any(),
                any(Pageable.class)
        );
    }
}
