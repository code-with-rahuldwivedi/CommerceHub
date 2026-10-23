
package com.shopsphere.inventoryservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopsphere.inventoryservice.entity.InventoryOperation;

@Repository
public interface InventoryOperationRepository
        extends JpaRepository<InventoryOperation, Long> {

    Optional<InventoryOperation> findByOperationKey(String operationKey);
}
