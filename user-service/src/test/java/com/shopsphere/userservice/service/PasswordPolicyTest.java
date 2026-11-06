package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.shopsphere.userservice.exception.InvalidRequestException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void strongPassword_isAccepted() {
        assertDoesNotThrow(() -> policy.validate("Str0ng-Passw0rd!", "rahul@example.com"));
        assertDoesNotThrow(() -> policy.validate("correct horse battery staple", "rahul@example.com"));
    }

    @Test
    void tooShort_isRejected() {
        assertThrows(InvalidRequestException.class, () -> policy.validate("short1!", "a@b.com"));
        assertThrows(InvalidRequestException.class, () -> policy.validate(null, "a@b.com"));
    }

    @Test
    void commonPassword_isRejected_caseInsensitively() {
        assertThrows(InvalidRequestException.class, () -> policy.validate("password123", "a@b.com"));
        assertThrows(InvalidRequestException.class, () -> policy.validate("PassWord123", "a@b.com"));
        assertThrows(InvalidRequestException.class, () -> policy.validate("12345678", "a@b.com"));
    }

    @Test
    void passwordContainingEmailName_isRejected() {
        assertThrows(InvalidRequestException.class,
                () -> policy.validate("xxRahulSharmaxx1", "rahulsharma@example.com"));
    }

    @Test
    void shortEmailLocalPart_doesNotTriggerTheContainsRule() {
        assertDoesNotThrow(() -> policy.validate("Str0ng-abc-Passw0rd", "abc@example.com"));
    }

    @Test
    void overBcryptByteLimit_isRejected_evenIfCharCountIsLow() {
        String multiByte = "é".repeat(40); // 40 chars, 80 bytes

        assertThrows(InvalidRequestException.class, () -> policy.validate(multiByte, "a@b.com"));
    }
}
