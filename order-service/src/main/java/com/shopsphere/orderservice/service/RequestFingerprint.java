package com.shopsphere.orderservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.shopsphere.orderservice.dto.AddressRequest;

/** Stable hash of what the customer asked for: tells an honest retry from reuse of an Idempotency-Key. */
public final class RequestFingerprint {

    private static final char SEPARATOR = '\u001f';

    private RequestFingerprint() {
    }

    public static String of(List<OrderLine> lines, AddressRequest address, String contactEmail) {
        StringBuilder canonical = new StringBuilder();
        lines.stream()
                .sorted(Comparator.comparingLong(OrderLine::productId))
                .forEach(l -> canonical.append(l.productId()).append(':').append(l.quantity()).append(';'));
        canonical.append('|');
        if (address != null) {
            for (String part : new String[] {address.fullName(), address.line1(), address.line2(), address.city(),
                    address.state(), address.postalCode(), address.country(), address.phone()}) {
                canonical.append(normalize(part)).append(SEPARATOR);
            }
        }
        canonical.append('|').append(normalize(contactEmail));
        return sha256(canonical.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
