package com.shopsphere.productservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.productservice.entity.StockOperation;

public interface StockOperationRepository
        extends JpaRepository<StockOperation, Long> {

    Optional<StockOperation> findByOperationKey(String operationKey);
}
