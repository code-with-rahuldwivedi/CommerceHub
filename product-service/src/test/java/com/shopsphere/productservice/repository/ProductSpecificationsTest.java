package com.shopsphere.productservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "h2"})
class ProductSpecificationsTest {

    @Autowired private ProductRepository products;

    @BeforeEach
    void seed() {
        products.deleteAll();
        save("Red Phone", "100.00", Category.MOBILES, ProductStatus.ACTIVE);
        save("Blue Phone", "300.00", Category.MOBILES, ProductStatus.ACTIVE);
        save("Novel", "10.00", Category.BOOKS, ProductStatus.ACTIVE);
        save("Hidden Phone", "50.00", Category.MOBILES, ProductStatus.INACTIVE);
        save("Old Phone", "5.00", Category.MOBILES, ProductStatus.ARCHIVED);
        save("100% Cotton Shirt", "20.00", Category.FASHION, ProductStatus.ACTIVE);
        save("100 Cotton Shirt", "21.00", Category.FASHION, ProductStatus.ACTIVE);
    }

    private void save(String name, String price, Category category, ProductStatus status) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("desc");
        product.setPrice(new BigDecimal(price));
        product.setCategory(category);
        product.setStatus(status);
        products.save(product);
    }

    private List<String> names(java.util.Collection<ProductStatus> statuses, Category category,
                               String min, String max, String text) {
        return products.findAll(
                        ProductSpecifications.matching(statuses, category,
                                min == null ? null : new BigDecimal(min),
                                max == null ? null : new BigDecimal(max), text),
                        PageRequest.of(0, 50, Sort.by("name")))
                .map(Product::getName).getContent();
    }

    @Test
    void onlyTheRequestedStatusesAreReturned() {
        assertEquals(5, names(EnumSet.of(ProductStatus.ACTIVE), null, null, null, null).size());
        assertEquals(List.of("Hidden Phone"), names(EnumSet.of(ProductStatus.INACTIVE), null, null, null, null));
        assertEquals(List.of("Old Phone"), names(EnumSet.of(ProductStatus.ARCHIVED), null, null, null, null));
    }

    @Test
    void categoryFilter() {
        assertEquals(List.of("Blue Phone", "Red Phone"),
                names(EnumSet.of(ProductStatus.ACTIVE), Category.MOBILES, null, null, null));
    }

    @Test
    void priceRange_isInclusive() {
        assertEquals(List.of("Blue Phone", "Red Phone"),
                names(EnumSet.of(ProductStatus.ACTIVE), null, "100.00", "300.00", null));
        assertEquals(List.of("Red Phone"),
                names(EnumSet.of(ProductStatus.ACTIVE), null, "100.00", "299.99", null));
    }

    @Test
    void nameSearch_isCaseInsensitiveContains() {
        assertEquals(List.of("Blue Phone", "Red Phone"),
                names(EnumSet.of(ProductStatus.ACTIVE), null, null, null, "  pHoNe "));
    }

    @Test
    void percentSign_isLiteral_notAWildcard() {
        assertEquals(List.of("100% Cotton Shirt"),
                names(EnumSet.of(ProductStatus.ACTIVE), null, null, null, "100%"));
    }

    @Test
    void underscore_isLiteral_notAWildcard() {
        assertEquals(List.of(), names(EnumSet.of(ProductStatus.ACTIVE), null, null, null, "R_d"));
    }

    @Test
    void sqlInjectionAttempt_isJustText() {
        assertEquals(List.of(), names(EnumSet.of(ProductStatus.ACTIVE), null, null, null, "x' OR '1'='1"));
    }

    @Test
    void filtersCombine() {
        assertEquals(List.of("Blue Phone"),
                names(EnumSet.of(ProductStatus.ACTIVE), Category.MOBILES, "200.00", null, "phone"));
    }
}
