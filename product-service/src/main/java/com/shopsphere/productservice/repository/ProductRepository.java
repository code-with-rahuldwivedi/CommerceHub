package com.shopsphere.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shopsphere.productservice.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
        UPDATE Product p
        SET p.stock = p.stock - :quantity
        WHERE p.id = :productId
          AND p.stock >= :quantity
        """)
    int reduceStock(
        @Param("productId") Long productId,
        @Param("quantity") Integer quantity
    );

    @Modifying
    @Query("""
        UPDATE Product p
        SET p.stock = p.stock + :quantity
        WHERE p.id = :productId
        """)
    int restoreStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );
}