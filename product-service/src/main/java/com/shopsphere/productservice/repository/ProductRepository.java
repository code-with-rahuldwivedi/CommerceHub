package com.shopsphere.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopsphere.productservice.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

}