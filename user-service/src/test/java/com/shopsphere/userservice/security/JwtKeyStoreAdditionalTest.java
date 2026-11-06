package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shopsphere.userservice.config.JwtProperties;

class JwtKeyStoreAdditionalTest {

    private static final String ISSUER = "svc";
    private static final String AUDIENCE = "api";

    private static KeyPair generateRsaKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static JwtProperties properties(
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

    @Test
    void nullPreviousKeys_areAccepted() {
        JwtKeyStore store = new JwtKeyStore(
                properties(null, null, true)
        );

        assertNotNull(store.signingKey());
        assertNotNull(store.currentPublicKey());
        assertEquals(1, store.jwks().get("keys") instanceof List<?> keys
                ? keys.size() : 0);
    }

    @Test
    void verificationKey_handlesNullAndUnknownKid() {
        JwtKeyStore store = new JwtKeyStore(
                properties(null, List.of(), true)
        );

        assertNull(store.verificationKey(null));
        assertNull(store.verificationKey("unknown-kid"));
        assertNotNull(store.verificationKey(store.currentKeyId()));
    }

    @Test
    void blankPreviousKeys_areSkipped() {
        JwtKeyStore store = new JwtKeyStore(
                properties(null, List.of("", " ", "\n"), true)
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys =
                (List<Map<String, Object>>) store.jwks().get("keys");

        assertEquals(1, keys.size());
    }

    @Test
    void duplicateCurrentPublicKey_isNotAddedTwice() throws Exception {
        KeyPair pair = generateRsaKeyPair(2048);

        String privateKey = encode(pair.getPrivate().getEncoded());
        String publicKey = encode(pair.getPublic().getEncoded());

        JwtKeyStore store = new JwtKeyStore(
                properties(privateKey, List.of(publicKey), false)
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys =
                (List<Map<String, Object>>) store.jwks().get("keys");

        assertEquals(1, keys.size());
        assertEquals(store.currentKeyId(), keys.get(0).get("kid"));
    }

    @Test
    void invalidPreviousPublicKey_failsAtStartup() throws Exception {
        KeyPair pair = generateRsaKeyPair(2048);

        String privateKey = encode(pair.getPrivate().getEncoded());

        assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(
                        properties(privateKey, List.of("invalid-key"), false)
                )
        );
    }

    @Test
    void weakPreviousPublicKey_failsAtStartup() throws Exception {
        KeyPair strongPair = generateRsaKeyPair(2048);
        KeyPair weakPair = generateRsaKeyPair(1024);

        String privateKey = encode(strongPair.getPrivate().getEncoded());
        String weakPublicKey = encode(weakPair.getPublic().getEncoded());

        assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(
                        properties(privateKey, List.of(weakPublicKey), false)
                )
        );
    }
    
    @Test
    void missingPrivateKeyAndEphemeralDisabled_failsAtStartup() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(properties(null, List.of(), false))
        );

        assertEquals(
                "jwt.private-key is required. Generate one with scripts/generate-jwt-key.sh and set JWT_PRIVATE_KEY.",
                exception.getMessage()
        );
    }

    @Test
    void pkcs1PrivateKey_isRejected() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(properties(
                        "-----BEGIN RSA PRIVATE KEY-----\\nAQID\\n-----END RSA PRIVATE KEY-----",
                        List.of(),
                        false
                ))
        );

        assertEquals(
                "jwt.private-key must be PKCS#8 ('BEGIN PRIVATE KEY'), not PKCS#1. Convert it: openssl pkcs8 -topk8 -nocrypt -in key.pem",
                exception.getMessage()
        );
    }

    @Test
    void weakCurrentPrivateKey_isRejected() throws Exception {
        KeyPair weakPair = generateRsaKeyPair(1024);
        String privateKey = encode(weakPair.getPrivate().getEncoded());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtKeyStore(properties(privateKey, List.of(), false))
        );

        assertEquals("RSA keys must be at least 2048 bits", exception.getMessage());
    }

    @Test
    void validPreviousPublicKey_isAddedToVerificationKeys() throws Exception {
        KeyPair currentPair = generateRsaKeyPair(2048);
        KeyPair previousPair = generateRsaKeyPair(2048);

        JwtKeyStore store = new JwtKeyStore(properties(
                encode(currentPair.getPrivate().getEncoded()),
                List.of(encode(previousPair.getPublic().getEncoded())),
                false
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys =
                (List<Map<String, Object>>) store.jwks().get("keys");

        assertEquals(2, keys.size());

        String previousKid = JwtKeyStore.keyId(
                (java.security.interfaces.RSAPublicKey) previousPair.getPublic()
        );

        assertNotNull(store.verificationKey(previousKid));
    }
}
