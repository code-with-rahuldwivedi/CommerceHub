package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.shopsphere.userservice.config.JwtProperties;

class JwtKeyStoreTest {

    private static final String ISSUER = "svc";
    private static final String AUDIENCE = "api";

    private static JwtProperties props(
            String privateKey,
            List<String> previousKeys,
            boolean ephemeral) {

        return new JwtProperties(
                privateKey,
                previousKeys,
                ephemeral,
                ISSUER,
                AUDIENCE,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Test
    void invalidPrivateKeyDer_isRejected() {
        String invalidDer = base64(new byte[]{1, 2, 3, 4});

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(props(invalidDer, List.of(), false))
        );

        assertEquals(
                "jwt.private-key is not a valid PKCS#8 RSA private key (PEM or Base64 DER)",
                exception.getMessage()
        );
    }

    @Test
    void invalidPreviousPublicKey_isRejected() throws Exception {
        KeyPair pair = generateKeyPair();
        String privateKey = base64(pair.getPrivate().getEncoded());
        String invalidPublicKey = base64(new byte[]{1, 2, 3, 4});

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(
                        props(privateKey, List.of(invalidPublicKey), false)
                )
        );

        assertEquals(
                "jwt.previous-public-keys contains an invalid X.509 RSA public key",
                exception.getMessage()
        );
    }

    @Test
    void nonRsaCrtPrivateKey_isRejected() throws Exception {
        KeyFactory factory = mock(KeyFactory.class);
        when(factory.generatePrivate(any(PKCS8EncodedKeySpec.class)))
                .thenReturn(mock(PrivateKey.class));

        try (MockedStatic<KeyFactory> keyFactoryMock =
                     org.mockito.Mockito.mockStatic(KeyFactory.class)) {

            keyFactoryMock.when(() -> KeyFactory.getInstance("RSA"))
                    .thenReturn(factory);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtKeyStore(
                            props(base64(new byte[]{1, 2, 3}), List.of(), false)
                    )
            );

            assertEquals(
                    "jwt.private-key must be an RSA CRT private key",
                    exception.getMessage()
            );
        }
    }

    @Test
    void ephemeralKeyGenerationFailure_throwsIllegalStateException() {
        try (MockedStatic<KeyPairGenerator> generatorMock =
                     org.mockito.Mockito.mockStatic(KeyPairGenerator.class)) {

            generatorMock.when(() -> KeyPairGenerator.getInstance("RSA"))
                    .thenThrow(new NoSuchAlgorithmException("RSA unavailable"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtKeyStore(props(null, List.of(), true))
            );

            assertEquals("RSA not available", exception.getMessage());
        }
    }

    @Test
    void publicKeyDerivationFailure_throwsIllegalStateException() throws Exception {
        KeyPair pair = generateKeyPair();
        String privateKey = base64(pair.getPrivate().getEncoded());

        KeyFactory realFactory = KeyFactory.getInstance("RSA");

        try (MockedStatic<KeyFactory> keyFactoryMock =
                     org.mockito.Mockito.mockStatic(KeyFactory.class)) {

            keyFactoryMock.when(() -> KeyFactory.getInstance("RSA"))
                    .thenReturn(realFactory)
                    .thenThrow(new NoSuchAlgorithmException("RSA unavailable"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtKeyStore(props(privateKey, List.of(), false))
            );

            assertEquals("Cannot derive public key", exception.getMessage());
        }
    }

    @Test
    void keyIdGenerationFailure_throwsIllegalStateException() throws Exception {
        KeyPair pair = generateKeyPair();
        String privateKey = base64(pair.getPrivate().getEncoded());

        try (MockedStatic<MessageDigest> digestMock =
                     org.mockito.Mockito.mockStatic(MessageDigest.class)) {

            digestMock.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 unavailable"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtKeyStore(props(privateKey, List.of(), false))
            );

            assertEquals("SHA-256 not available", exception.getMessage());
        }
    }
}
