package com.shopsphere.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class InitializeInventoryRequest {

    @NotNull(message = "Initial quantity is required")
    @PositiveOrZero(message = "Initial quantity cannot be negative")
    private Integer initialQuantity;

    public InitializeInventoryRequest() {
    }

    public Integer getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(Integer initialQuantity) {
        this.initialQuantity = initialQuantity;
    }
}
