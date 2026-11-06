
package com.shopsphere.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.inventoryservice.entity.InventoryItem;
import com.shopsphere.inventoryservice.repository.InventoryItemRepository;
import com.shopsphere.inventoryservice.repository.InventoryOperationRepository;
import com.shopsphere.inventoryservice.repository.InventoryOutboxRepository;

@SpringBootTest
class InventoryServiceIntegrationTests {

    private static final Long PRODUCT_ID = 1001L;
    private static final int INITIAL_STOCK = 10;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryOperationRepository operationRepository;

    @Autowired
    private InventoryOutboxRepository outboxRepository;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        operationRepository.deleteAll();
        inventoryItemRepository.deleteAll();

        inventoryService.initializeInventory(
                PRODUCT_ID, INITIAL_STOCK);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();

            try {
                if (!executorService.awaitTermination(
                        5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Test executor did not terminate");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "Interrupted while shutting down executor",
                        exception);
            }
        }
    }

    // 1. Insufficient stock handling
    @Test
    void shouldRejectReductionWhenStockIsInsufficient() {

        assertThatThrownBy(() ->
                inventoryService.reduceStock(
                        PRODUCT_ID,
                        15,
                        "insufficient-stock-001"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response =
                            (ResponseStatusException) exception;

                    assertThat(response.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(getAvailableStock())
                .isEqualTo(INITIAL_STOCK);
    }

    // 2. Duplicate stock reduction
    @Test
    void shouldReduceStockOnlyOnceForDuplicateOperation() {

        inventoryService.reduceStock(
                PRODUCT_ID, 3, "duplicate-reduce-001");

        inventoryService.reduceStock(
                PRODUCT_ID, 3, "duplicate-reduce-001");

        assertThat(getAvailableStock()).isEqualTo(7);

        assertThat(operationRepository.findAll())
                .hasSize(1);
    }

    // 3. Duplicate stock restoration
    @Test
    void shouldRestoreStockOnlyOnceForDuplicateOperation() {

        inventoryService.reduceStock(
                PRODUCT_ID, 3, "order-101-reduce");

        inventoryService.restoreStock(
                PRODUCT_ID, 3, "order-101-restore");

        inventoryService.restoreStock(
                PRODUCT_ID, 3, "order-101-restore");

        assertThat(getAvailableStock())
                .isEqualTo(INITIAL_STOCK);

        assertThat(operationRepository.findAll())
                .hasSize(2);
    }

    // 4. Same operation key with different request
    @Test
    void shouldRejectSameOperationKeyWithDifferentRequest() {

        inventoryService.reduceStock(
                PRODUCT_ID, 2, "same-key-001");

        assertThatThrownBy(() ->
                inventoryService.reduceStock(
                        PRODUCT_ID, 4, "same-key-001"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response =
                            (ResponseStatusException) exception;

                    assertThat(response.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(getAvailableStock()).isEqualTo(8);

        assertThat(operationRepository.findAll())
                .hasSize(1);
    }

    // 5. Concurrent stock reduction - prevent overselling
    @Test
    void shouldPreventOversellingDuringConcurrentReductions()
            throws Exception {

        executorService = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> firstRequest = () ->
                    attemptReduction(
                            "concurrent-reduce-001",
                            ready,
                            start);

            Callable<Boolean> secondRequest = () ->
                    attemptReduction(
                            "concurrent-reduce-002",
                            ready,
                            start);

            Future<Boolean> first =
                    executorService.submit(firstRequest);

            Future<Boolean> second =
                    executorService.submit(secondRequest);

            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("Both requests should be ready")
                    .isTrue();

            // Start both requests simultaneously.
            start.countDown();

            List<Boolean> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            long successfulRequests = results.stream()
                    .filter(Boolean::booleanValue)
                    .count();

            long rejectedRequests = results.stream()
                    .filter(result -> !result)
                    .count();

            // Exactly one request should succeed.
            assertThat(successfulRequests).isEqualTo(1);

            // Exactly one request should be rejected.
            assertThat(rejectedRequests).isEqualTo(1);

            // Only one reduction of 6 should be committed.
            assertThat(getAvailableStock()).isEqualTo(4);

            // Only one successful operation should be recorded.
            assertThat(operationRepository.findAll())
                    .hasSize(1);

        } finally {
            start.countDown();
        }
    }

    private boolean attemptReduction(
            String operationKey,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Concurrent test start signal timed out");
        }

        try {
            // IMPORTANT: Each request tries to reduce 6 units.
            inventoryService.reduceStock(
                    PRODUCT_ID, 6, operationKey);

            return true;

        } catch (ResponseStatusException exception) {

            if (exception.getStatusCode()
                    .equals(HttpStatus.CONFLICT)) {
                return false;
            }

            throw exception;
        }
    }

    private int getAvailableStock() {

        InventoryItem item =
                inventoryItemRepository.findByProductId(PRODUCT_ID)
                        .orElseThrow(() ->
                                new AssertionError(
                                        "Inventory item not found"));

        return item.getAvailableQuantity();
    }
}
