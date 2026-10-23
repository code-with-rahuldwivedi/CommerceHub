
package com.shopsphere.inventoryservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shopsphere.inventoryservice.entity.InventoryItem;

@Repository
public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductId(Long productId);

    @Modifying
    @Query(value = """
        UPDATE inventory_items
        SET available_quantity = available_quantity - :quantity,
            version = version + 1,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE product_id = :productId
          AND available_quantity >= :quantity
        """, nativeQuery = true)
    int reduceStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);

    @Modifying
    @Query(value = """
        UPDATE inventory_items
        SET available_quantity = available_quantity + :quantity,
            version = version + 1,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE product_id = :productId
          AND available_quantity <= 2147483647 - :quantity
        """, nativeQuery = true)
    int restoreStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);
}
