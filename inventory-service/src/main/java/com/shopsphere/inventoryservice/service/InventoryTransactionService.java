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
    private final InventoryResultEventService inventoryResultEventService;

    public InventoryTransactionService(
            InventoryItemRepository inventoryItemRepository,
            InventoryOperationRepository inventoryOperationRepository,
            InventoryResultEventService inventoryResultEventService) {

        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryOperationRepository = inventoryOperationRepository;
        this.inventoryResultEventService = inventoryResultEventService;
    }

    @Transactional
    public InventoryItem reduceStock(
            Long productId,
            Integer quantity,
            String operationKey) {

        validateRequest(productId, quantity, operationKey);

        Optional<InventoryOperation> existing =
                inventoryOperationRepository.findByOperationKey(operationKey);

        // Idempotency: never reduce stock twice for the same operation.
        if (existing.isPresent()) {
            validateSameOperation(
                    existing.get(),
                    productId,
                    quantity,
                    InventoryOperationType.REDUCE);

            // Repair a missing success event for a valid order operation.
            recordReservationSuccessIfOrderOperation(
                    operationKey, productId, quantity);

            return getInventoryOrThrow(productId);
        }

        InventoryOperation operation = new InventoryOperation(
                operationKey,
                productId,
                quantity,
                InventoryOperationType.REDUCE);

        inventoryOperationRepository.saveAndFlush(operation);

        int updated = inventoryItemRepository.reduceStock(productId, quantity);

        if (updated == 0) {
            if (inventoryItemRepository.findByProductId(productId).isEmpty()) {
                String message =
                        "Inventory not found for product: " + productId;

                recordReservationFailureIfOrderOperation(
                        operationKey, productId, quantity, message);

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message);
            }

            String message = "Insufficient stock for product: " + productId;

            recordReservationFailureIfOrderOperation(
                    operationKey, productId, quantity, message);

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, message);
        }

        InventoryItem updatedItem = getInventoryOrThrow(productId);

        // Stock change and success outbox event use the same transaction.
        recordReservationSuccessIfOrderOperation(
                operationKey, productId, quantity);

        return updatedItem;
    }

    private void recordReservationSuccessIfOrderOperation(
            String operationKey,
            Long productId,
            Integer quantity) {

        Long orderId = extractOrderIdIfOrderReduceKey(operationKey);

        if (orderId == null) {
            return;
        }

        inventoryResultEventService.recordReservationSuccess(
                orderId, productId, quantity);
    }

    private void recordReservationFailureIfOrderOperation(
            String operationKey,
            Long productId,
            Integer quantity,
            String message) {

        Long orderId = extractOrderIdIfOrderReduceKey(operationKey);

        if (orderId == null) {
            return;
        }

        // InventoryResultEventService uses REQUIRES_NEW for failure events.
        inventoryResultEventService.recordReservationFailure(
                orderId, productId, quantity, message);
    }

    /**
     * Accept only the exact format: order-{positiveOrderId}-reduce.
     * A security-test key or a combined key is not an order key.
     */
    private Long extractOrderIdIfOrderReduceKey(String operationKey) {

        String prefix = "order-";
        String suffix = "-reduce";

        if (operationKey == null
                || !operationKey.startsWith(prefix)
                || !operationKey.endsWith(suffix)) {
            return null;
        }

        String id = operationKey.substring(
                prefix.length(),
                operationKey.length() - suffix.length());

        // Reject empty IDs and IDs containing extra separators or text.
        if (id.isBlank() || !id.matches("[0-9]+")) {
            return null;
        }

        try {
            long orderId = Long.parseLong(id);
            return orderId > 0 ? orderId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
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
                    existing.get(),
                    productId,
                    quantity,
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
            if (inventoryItemRepository.findByProductId(productId).isEmpty()) {
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
                    "Product ID must be greater than zero.");
        }

        if (initialQuantity == null || initialQuantity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial quantity cannot be negative.");
        }

        if (inventoryItemRepository.findByProductId(productId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inventory already exists for this product");
        }

        InventoryItem item = new InventoryItem(productId, initialQuantity);
        return inventoryItemRepository.saveAndFlush(item);
    }
}
