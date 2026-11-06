package com.shopsphere.userservice.util;

public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** a***@example.com : keeps logs useful without storing full PII, and strips control characters. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        String clean = email.replaceAll("\\p{Cntrl}", "_");
        int at = clean.indexOf('@');
        if (at <= 0) {
            return clean.charAt(0) + "***";
        }
        return clean.charAt(0) + "***" + clean.substring(at);
    }
}
