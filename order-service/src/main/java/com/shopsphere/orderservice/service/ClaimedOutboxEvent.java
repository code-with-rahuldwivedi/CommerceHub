package com.shopsphere.orderservice.service;

public record ClaimedOutboxEvent(Long id, Long aggregateId, String payload, int attempts) {
}
