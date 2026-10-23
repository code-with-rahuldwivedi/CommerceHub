package com.shopsphere.inventoryservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "inventory_operations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_inventory_operation_key",
            columnNames = "operation_key"
        )
    }
)
public class InventoryOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_key", nullable = false, length = 150)
    private String operationKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private InventoryOperationType operationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventoryOperation() {
    }

    public InventoryOperation(
            String operationKey,
            Long productId,
            Integer quantity,
            InventoryOperationType operationType) {

        this.operationKey = operationKey;
        this.productId = productId;
        this.quantity = quantity;
        this.operationType = operationType;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public InventoryOperationType getOperationType() {
        return operationType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}