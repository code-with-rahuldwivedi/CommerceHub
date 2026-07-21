package com.shopsphere.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopsphere.orderservice.entity.Order;

public interface OrderRepository  extends JpaRepository<Order, Long>{

}
