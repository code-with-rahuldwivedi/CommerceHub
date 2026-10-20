package com.shopsphere.productservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "stock_operations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_stock_operation_key",
            columnNames = "operation_key"
        )
    }
)
public class StockOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_key", nullable = false, length = 150)
    private String operationKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType operationType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected StockOperation() {
    }

    public StockOperation(
            String operationKey,
            Long productId,
            Integer quantity,
            OperationType operationType) {
        this.operationKey = operationKey;
        this.productId = productId;
        this.quantity = quantity;
        this.operationType = operationType;
        this.createdAt = LocalDateTime.now();
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

    public OperationType getOperationType() {
        return operationType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
