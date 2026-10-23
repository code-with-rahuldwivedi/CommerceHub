
package com.shopsphere.inventoryservice.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.inventoryservice.entity.InventoryItem;
import com.shopsphere.inventoryservice.entity.InventoryOperation;
import com.shopsphere.inventoryservice.entity.InventoryOperationType;
import com.shopsphere.inventoryservice.repository.InventoryOperationRepository;

@Service
public class InventoryService {

    private final InventoryTransactionService transactionService;
    private final InventoryOperationRepository operationRepository;

    public InventoryService(
            InventoryTransactionService transactionService,
            InventoryOperationRepository operationRepository) {
        this.transactionService = transactionService;
        this.operationRepository = operationRepository;
    }

    public InventoryItem reduceStock(
            Long productId,
            Integer quantity,
            String operationKey) {

        String key = normalizeKey(operationKey);

        try {
            return transactionService.reduceStock(productId, quantity, key);
        } catch (DataIntegrityViolationException exception) {
            return resolveDuplicate(
                    productId, quantity, key, InventoryOperationType.REDUCE,
                    exception);
        }
    }

    public InventoryItem restoreStock(
            Long productId,
            Integer quantity,
            String operationKey) {

        String key = normalizeKey(operationKey);

        try {
            return transactionService.restoreStock(productId, quantity, key);
        } catch (DataIntegrityViolationException exception) {
            return resolveDuplicate(
                    productId, quantity, key, InventoryOperationType.RESTORE,
                    exception);
        }
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventory(Long productId) {
        return transactionService.getInventory(productId);
    }

    private InventoryItem resolveDuplicate(
            Long productId,
            Integer quantity,
            String key,
            InventoryOperationType operationType,
            DataIntegrityViolationException originalException) {

        InventoryOperation existing = operationRepository.findByOperationKey(key)
                .orElseThrow(() -> originalException);

        if (!existing.getProductId().equals(productId)
                || !existing.getQuantity().equals(quantity)
                || existing.getOperationType() != operationType) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key has already been used for a different operation.");
        }

        return transactionService.getInventory(productId);
    }

    private String normalizeKey(String operationKey) {
        if (operationKey == null) {
            return null;
        }
        return operationKey.trim();
    }
    

	public InventoryItem initializeInventory(
	        Long productId,
	        Integer initialQuantity) {
	
	    return transactionService.initializeInventory(
	            productId,
	            initialQuantity
	    );
}

}
