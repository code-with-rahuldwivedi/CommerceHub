
package com.shopsphere.inventoryservice.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.inventoryservice.entity.InventoryItem;
import com.shopsphere.inventoryservice.entity.InventoryOperation;
import com.shopsphere.inventoryservice.entity.InventoryOperationType;
import com.shopsphere.inventoryservice.repository.InventoryItemRepository;
import com.shopsphere.inventoryservice.repository.InventoryOperationRepository;

@Service
public class InventoryTransactionService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryOperationRepository inventoryOperationRepository;

    public InventoryTransactionService(
            InventoryItemRepository inventoryItemRepository,
            InventoryOperationRepository inventoryOperationRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryOperationRepository = inventoryOperationRepository;
    }

    @Transactional
    public InventoryItem reduceStock(
            Long productId,
            Integer quantity,
            String operationKey) {

        validateRequest(productId, quantity, operationKey);

        Optional<InventoryOperation> existing =
                inventoryOperationRepository.findByOperationKey(operationKey);

        if (existing.isPresent()) {
            validateSameOperation(
                    existing.get(), productId, quantity,
                    InventoryOperationType.REDUCE);

            return getInventoryOrThrow(productId);
        }

        // Claim the idempotency key before changing stock.
        // A duplicate key causes this transaction to roll back.
        inventoryOperationRepository.saveAndFlush(
                new InventoryOperation(
                        operationKey,
                        productId,
                        quantity,
                        InventoryOperationType.REDUCE));

        int updated = inventoryItemRepository.reduceStock(productId, quantity);

        if (updated == 0) {
            if (!inventoryItemRepository.findByProductId(productId).isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient stock for product: " + productId);
        }

        return getInventoryOrThrow(productId);
    }

    @Transactional
    public InventoryItem restoreStock(
            Long productId,
            Integer quantity,
            String operationKey) {

        validateRequest(productId, quantity, operationKey);

        Optional<InventoryOperation> existing =
                inventoryOperationRepository.findByOperationKey(operationKey);

        if (existing.isPresent()) {
            validateSameOperation(
                    existing.get(), productId, quantity,
                    InventoryOperationType.RESTORE);

            return getInventoryOrThrow(productId);
        }

        inventoryOperationRepository.saveAndFlush(
                new InventoryOperation(
                        operationKey,
                        productId,
                        quantity,
                        InventoryOperationType.RESTORE));

        int updated = inventoryItemRepository.restoreStock(productId, quantity);

        if (updated == 0) {
            if (!inventoryItemRepository.findByProductId(productId).isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Restoring this quantity would exceed the supported stock limit.");
        }

        return getInventoryOrThrow(productId);
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventory(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID must be positive.");
        }

        return getInventoryOrThrow(productId);
    }

    private InventoryItem getInventoryOrThrow(Long productId) {
        return inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));
    }

    private void validateRequest(
            Long productId,
            Integer quantity,
            String operationKey) {

        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID must be positive.");
        }

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be positive.");
        }

        if (operationKey == null || operationKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key is required.");
        }

        if (operationKey.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key must not exceed 150 characters.");
        }
    }

    private void validateSameOperation(
            InventoryOperation existing,
            Long productId,
            Integer quantity,
            InventoryOperationType operationType) {

        if (!existing.getProductId().equals(productId)
                || !existing.getQuantity().equals(quantity)
                || existing.getOperationType() != operationType) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key has already been used for a different operation.");
        }
    }
    
    @Transactional
    public InventoryItem initializeInventory(
            Long productId,
            Integer initialQuantity) {

        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID must be greater than zero"
            );
        }

        if (initialQuantity == null || initialQuantity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial quantity cannot be negative"
            );
        }

        if (inventoryItemRepository.findByProductId(productId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory already exists for this product"
            );
        }

        InventoryItem item = new InventoryItem(productId, initialQuantity);

        return inventoryItemRepository.saveAndFlush(item);
    }
}
