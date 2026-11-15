package com.shopsphere.orderservice.service;

/** A normalized requested line: one entry per product, quantities merged. */
public record OrderLine(long productId, int quantity) {
}
