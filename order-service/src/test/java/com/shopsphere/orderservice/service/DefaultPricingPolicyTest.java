package com.shopsphere.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shopsphere.orderservice.dto.AddressRequest;

class DefaultPricingPolicyAndFingerprintTest {

    @Test
    void defaultPricingPolicy_sumsLinesWithHalfUpRounding() {
        DefaultPricingPolicy policy = new DefaultPricingPolicy();
        var lines = List.of(
                new PricingPolicy.PricingLine(1L, 2, new BigDecimal("19.995")),
                new PricingPolicy.PricingLine(2L, 3, new BigDecimal("10.00"))
        );
        PricingPolicy.PriceBreakdown breakdown = policy.price(
                new PricingPolicy.PricingContext(1L, "INR", lines, null));

        // 2 * 19.995 = 39.990 + 30.00 = 69.99
        assertThat(breakdown.subtotal()).isEqualByComparingTo("69.99");
        assertThat(breakdown.shipping()).isEqualByComparingTo("0.00");
        assertThat(breakdown.tax()).isEqualByComparingTo("0.00");
        assertThat(breakdown.discount()).isEqualByComparingTo("0.00");
        assertThat(breakdown.total()).isEqualByComparingTo("69.99");
    }

    @Test
    void requestFingerprint_isDeterministicAcrossLineOrderAndCase() {
        AddressRequest addr1 = new AddressRequest(" John Doe ", "1 Main St", null, "Bengaluru", "KA", "560001", "IN", null);
        AddressRequest addr2 = new AddressRequest("john doe", "1 main st", "", "BENGALURU", "ka", "560001", "in", " ");

        String hash1 = RequestFingerprint.of(
                List.of(new OrderLine(20L, 1), new OrderLine(10L, 3)),
                addr1,
                " USER@Example.com ");
        String hash2 = RequestFingerprint.of(
                List.of(new OrderLine(10L, 3), new OrderLine(20L, 1)),
                addr2,
                "user@example.com");
        String differentHash = RequestFingerprint.of(
                List.of(new OrderLine(10L, 4), new OrderLine(20L, 1)),
                addr1,
                "user@example.com");

        assertThat(hash1).hasSize(64).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(differentHash);
        assertThat(RequestFingerprint.of(List.of(new OrderLine(10L, 1)), null, null)).hasSize(64);
    }
}
