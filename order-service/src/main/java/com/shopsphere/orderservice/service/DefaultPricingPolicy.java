package com.shopsphere.orderservice.service;

import java.math.BigDecimal;

/** Subtotal = sum of lines; no shipping, tax or discount. */
public class DefaultPricingPolicy implements PricingPolicy {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Override
    public PriceBreakdown price(PricingContext context) {
        BigDecimal subtotal = context.lines().stream()
                .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return new PriceBreakdown(subtotal, ZERO, ZERO, ZERO);
    }
}
