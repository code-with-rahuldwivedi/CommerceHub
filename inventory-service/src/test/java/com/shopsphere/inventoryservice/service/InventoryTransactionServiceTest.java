package com.shopsphere.inventoryservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.inventoryservice.entity.InventoryItem;
import com.shopsphere.inventoryservice.entity.InventoryOperation;
import com.shopsphere.inventoryservice.entity.InventoryOperationType;
import com.shopsphere.inventoryservice.repository.InventoryItemRepository;
import com.shopsphere.inventoryservice.repository.InventoryOperationRepository;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryOperationRepository inventoryOperationRepository;

    @Mock
    private InventoryResultEventService inventoryResultEventService;

    @InjectMocks
    private InventoryTransactionService inventoryTransactionService;

    private InventoryItem inventoryItem;

    @BeforeEach
    void setUp() {
        inventoryItem = new InventoryItem(101L, 10);
    }

    @Test
    void reduceStock_shouldReduceQuantityForNewOperation() {
        when(inventoryOperationRepository.findByOperationKey("manual-reduce-1"))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.reduceStock(101L, 3)).thenReturn(1);
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(new InventoryItem(101L, 7)));

        InventoryItem result = inventoryTransactionService
                .reduceStock(101L, 3, "manual-reduce-1");

        assertEquals(7, result.getAvailableQuantity());
        verify(inventoryOperationRepository).saveAndFlush(any(InventoryOperation.class));
        verify(inventoryItemRepository).reduceStock(101L, 3);
    }

    @Test
    void reduceStock_shouldNotReduceTwiceForSameOperation() {
        InventoryOperation existing = new InventoryOperation(
                "manual-reduce-2", 101L, 3, InventoryOperationType.REDUCE);
        when(inventoryOperationRepository.findByOperationKey("manual-reduce-2"))
                .thenReturn(Optional.of(existing));
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(inventoryItem));

        InventoryItem result = inventoryTransactionService
                .reduceStock(101L, 3, "manual-reduce-2");

        assertEquals(10, result.getAvailableQuantity());
        verify(inventoryItemRepository, never()).reduceStock(any(), any());
        verify(inventoryOperationRepository, never()).saveAndFlush(any());
    }

    @Test
    void reduceStock_shouldRejectReusingKeyForDifferentOperation() {
        InventoryOperation existing = new InventoryOperation(
                "manual-reduce-3", 101L, 2, InventoryOperationType.REDUCE);
        when(inventoryOperationRepository.findByOperationKey("manual-reduce-3"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(101L, 4, "manual-reduce-3"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void reduceStock_shouldRejectInsufficientStockAndRecordOrderFailure() {
        when(inventoryOperationRepository.findByOperationKey("order-501-reduce"))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.reduceStock(101L, 12)).thenReturn(0);
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(inventoryItem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(101L, 12, "order-501-reduce"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryResultEventService).recordReservationFailure(
                501L, 101L, 12, "Insufficient stock for product: 101");
    }

    @Test
    void reduceStock_shouldReturnNotFoundWhenInventoryDoesNotExist() {
        when(inventoryOperationRepository.findByOperationKey("order-502-reduce"))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.reduceStock(999L, 1)).thenReturn(0);
        when(inventoryItemRepository.findByProductId(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(999L, 1, "order-502-reduce"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(inventoryResultEventService).recordReservationFailure(
                502L, 999L, 1, "Inventory not found for product: 999");
    }

    @Test
    void restoreStock_shouldIncreaseQuantityForNewOperation() {
        when(inventoryOperationRepository.findByOperationKey("manual-restore-1"))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.restoreStock(101L, 4)).thenReturn(1);
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(new InventoryItem(101L, 14)));

        InventoryItem result = inventoryTransactionService
                .restoreStock(101L, 4, "manual-restore-1");

        assertEquals(14, result.getAvailableQuantity());
        verify(inventoryOperationRepository).saveAndFlush(any(InventoryOperation.class));
        verify(inventoryItemRepository).restoreStock(101L, 4);
    }

    @Test
    void restoreStock_shouldNotRestoreTwiceForSameOperation() {
        InventoryOperation existing = new InventoryOperation(
                "manual-restore-2", 101L, 4, InventoryOperationType.RESTORE);
        when(inventoryOperationRepository.findByOperationKey("manual-restore-2"))
                .thenReturn(Optional.of(existing));
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(inventoryItem));

        InventoryItem result = inventoryTransactionService
                .restoreStock(101L, 4, "manual-restore-2");

        assertEquals(10, result.getAvailableQuantity());
        verify(inventoryItemRepository, never()).restoreStock(any(), any());
        verify(inventoryOperationRepository, never()).saveAndFlush(any());
    }

    @Test
    void restoreStock_shouldRejectReusingKeyForDifferentOperation() {
        InventoryOperation existing = new InventoryOperation(
                "manual-restore-3", 101L, 2, InventoryOperationType.RESTORE);
        when(inventoryOperationRepository.findByOperationKey("manual-restore-3"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.restoreStock(101L, 3, "manual-restore-3"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void restoreStock_shouldRejectIntegerOverflow() {
        when(inventoryOperationRepository.findByOperationKey("manual-restore-4"))
                .thenReturn(Optional.empty());
        when(inventoryItemRepository.restoreStock(101L, 1)).thenReturn(0);
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(inventoryItem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.restoreStock(101L, 1, "manual-restore-4"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void shouldRejectInvalidProductQuantityAndOperationKey() {
        ResponseStatusException invalidProduct = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(0L, 1, "key-1"));
        ResponseStatusException invalidQuantity = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(101L, 0, "key-2"));
        ResponseStatusException missingKey = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.reduceStock(101L, 1, " "));

        assertEquals(HttpStatus.BAD_REQUEST, invalidProduct.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidQuantity.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, missingKey.getStatusCode());
        verifyNoInteractions(inventoryItemRepository, inventoryOperationRepository);
    }

    @Test
    void initializeInventory_shouldCreateNewInventory() {
        when(inventoryItemRepository.findByProductId(101L)).thenReturn(Optional.empty());
        when(inventoryItemRepository.saveAndFlush(any(InventoryItem.class)))
                .thenReturn(inventoryItem);

        InventoryItem result = inventoryTransactionService.initializeInventory(101L, 10);

        assertEquals(101L, result.getProductId());
        assertEquals(10, result.getAvailableQuantity());
    }

    @Test
    void initializeInventory_shouldRejectExistingProduct() {
        when(inventoryItemRepository.findByProductId(101L))
                .thenReturn(Optional.of(inventoryItem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.initializeInventory(101L, 10));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void getInventory_shouldRejectInvalidProductId() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryTransactionService.getInventory(-1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Product ID"));
        verifyNoInteractions(inventoryItemRepository);
    }
}
