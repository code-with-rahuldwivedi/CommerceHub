package com.shopsphere.userservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void newRawToken_generatesUrlSafeTokenWithoutPadding() {
        String token = TokenHasher.newRawToken();

        assertNotNull(token);
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertTrue(!token.contains("="));
    }

    @Test
    void newRawToken_generatesUniqueTokens() {
        String firstToken = TokenHasher.newRawToken();
        String secondToken = TokenHasher.newRawToken();

        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void hash_returnsCorrectSha256Hash() {
        String hash = TokenHasher.hash("hello");

        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                hash
        );
    }

    @Test
    void hash_returnsSameHashForSameInput() {
        String firstHash = TokenHasher.hash("rahul-token");
        String secondHash = TokenHasher.hash("rahul-token");

        assertEquals(firstHash, secondHash);
    }

    @Test
    void hash_returnsDifferentHashesForDifferentInputs() {
        String firstHash = TokenHasher.hash("token-one");
        String secondHash = TokenHasher.hash("token-two");

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void hash_returnsExpectedHashForEmptyString() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb924"
                        + "27ae41e4649b934ca495991b7852b855",
                TokenHasher.hash("")
        );
    }

    @Test
    void hash_isCaseSensitive() {
        assertNotEquals(
                TokenHasher.hash("Rahul"),
                TokenHasher.hash("rahul")
        );
    }

    @Test
    void constructor_isPrivate() throws Exception {
        Constructor<TokenHasher> constructor =
                TokenHasher.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
    
    @Test
    void hash_throwsIllegalStateExceptionWhenSha256IsUnavailable() {
        try (MockedStatic<MessageDigest> mockedDigest =
                     mockStatic(MessageDigest.class)) {

            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 unavailable"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> TokenHasher.hash("hello")
            );

            assertEquals("SHA-256 not available", exception.getMessage());
            assertTrue(exception.getCause() instanceof NoSuchAlgorithmException);
        }
    }
}
