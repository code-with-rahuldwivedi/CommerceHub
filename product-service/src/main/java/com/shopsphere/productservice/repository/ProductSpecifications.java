package com.shopsphere.productservice.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;

import jakarta.persistence.criteria.Predicate;

/** Dynamic catalog filters. Every user-supplied value is bound as a parameter (no string concatenation). */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '!';

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(Collection<ProductStatus> statuses,
                                                  Category category,
                                                  BigDecimal minPrice,
                                                  BigDecimal maxPrice,
                                                  String text) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(root.get("status").in(statuses));
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), maxPrice));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("name")), pattern, LIKE_ESCAPE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** So that a search for "50%" finds "50%" instead of acting as a wildcard. */
    static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
