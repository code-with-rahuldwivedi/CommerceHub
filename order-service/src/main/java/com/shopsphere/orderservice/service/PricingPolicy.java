package com.shopsphere.orderservice.service;

import java.math.BigDecimal;
import java.util.List;

import com.shopsphere.orderservice.dto.AddressRequest;

/**
 * Extension point for shipping, tax and discounts. The default implementation charges nothing extra; a future
 * pricing / promotion / tax service plugs in by providing its own PricingPolicy bean, without touching order code.
 */
public interface PricingPolicy {

    PriceBreakdown price(PricingContext context);

    record PricingLine(long productId, int quantity, BigDecimal unitPrice) {
    }

    record PricingContext(long userId, String currency, List<PricingLine> lines, AddressRequest shippingAddress) {
    }

    record PriceBreakdown(BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal discount) {

        public BigDecimal total() {
            return subtotal.add(shipping).add(tax).subtract(discount);
        }
    }
}
