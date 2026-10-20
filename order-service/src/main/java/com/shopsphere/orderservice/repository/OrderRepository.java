package com.shopsphere.orderservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.orderservice.entity.Order;

public interface OrderRepository  extends JpaRepository<Order, Long>{

	Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
