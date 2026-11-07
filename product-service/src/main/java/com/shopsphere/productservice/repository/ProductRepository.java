package com.shopsphere.productservice.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);
}
