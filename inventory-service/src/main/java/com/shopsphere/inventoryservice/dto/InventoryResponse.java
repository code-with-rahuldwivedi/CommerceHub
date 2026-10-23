
package com.shopsphere.inventoryservice.dto;

import java.time.LocalDateTime;

public class InventoryResponse {

    private Long productId;
    private Integer availableQuantity;
    private Long version;
    private LocalDateTime updatedAt;

    public InventoryResponse() {
    }

    public InventoryResponse(
            Long productId,
            Integer availableQuantity,
            Long version,
            LocalDateTime updatedAt) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
