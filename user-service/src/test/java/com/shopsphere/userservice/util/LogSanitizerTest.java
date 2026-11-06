package com.shopsphere.userservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void maskEmail_returnsEmptyForNull() {
        assertEquals("<empty>", LogSanitizer.maskEmail(null));
    }

    @Test
    void maskEmail_returnsEmptyForBlank() {
        assertEquals("<empty>", LogSanitizer.maskEmail("   "));
    }

    @Test
    void maskEmail_masksValidEmail() {
        assertEquals("r***@example.com",
                LogSanitizer.maskEmail("rahul@example.com"));
    }

    @Test
    void maskEmail_masksEmailWithoutAtSymbol() {
        assertEquals("r***",
                LogSanitizer.maskEmail("rahul"));
    }

    @Test
    void maskEmail_masksEmailStartingWithAtSymbol() {
        assertEquals("@***",
                LogSanitizer.maskEmail("@example.com"));
    }

    @Test
    void maskEmail_replacesControlCharacters() {
        assertEquals("r***@exa_mple.com",
                LogSanitizer.maskEmail("rahul@exa\nmple.com"));
    }

    @Test
    void maskEmail_handlesSingleCharacterEmail() {
        assertEquals("r***@example.com",
                LogSanitizer.maskEmail("r@example.com"));
    }
}