package com.shopsphere.inventoryservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.inventoryservice.dto.InitializeInventoryRequest;
import com.shopsphere.inventoryservice.dto.InventoryResponse;
import com.shopsphere.inventoryservice.dto.StockAdjustmentRequest;
import com.shopsphere.inventoryservice.entity.InventoryItem;
import com.shopsphere.inventoryservice.service.InventoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{productId}/initialize")
    public ResponseEntity<InventoryResponse> initializeInventory(
            @PathVariable Long productId,
            @Valid @RequestBody InitializeInventoryRequest request) {

        InventoryItem item = inventoryService.initializeInventory(
                productId,
                request.getInitialQuantity()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(item));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(
            @PathVariable Long productId) {

        InventoryItem item = inventoryService.getInventory(productId);

        return ResponseEntity.ok(toResponse(item));
    }

    @PostMapping("/{productId}/reduce")
    public ResponseEntity<InventoryResponse> reduceStock(
            @PathVariable Long productId,
            @RequestHeader("Idempotency-Key") String operationKey,
            @Valid @RequestBody StockAdjustmentRequest request) {

        InventoryItem item = inventoryService.reduceStock(
                productId,
                request.getQuantity(),
                operationKey
        );

        return ResponseEntity.ok(toResponse(item));
    }

    @PostMapping("/{productId}/restore")
    public ResponseEntity<InventoryResponse> restoreStock(
            @PathVariable Long productId,
            @RequestHeader("Idempotency-Key") String operationKey,
            @Valid @RequestBody StockAdjustmentRequest request) {

        InventoryItem item = inventoryService.restoreStock(
                productId,
                request.getQuantity(),
                operationKey
        );

        return ResponseEntity.ok(toResponse(item));
    }

    private InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(
                item.getProductId(),
                item.getAvailableQuantity(),
                item.getVersion(),
                item.getUpdatedAt()
        );
    }
}
