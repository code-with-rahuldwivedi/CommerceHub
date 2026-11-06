package com.shopsphere.userservice.security;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopsphere.userservice.config.JwtProperties;

/**
 * Holds the RSA signing key and the public keys used for verification/JWKS.
 * <p>
 * The key id ("kid") is derived from the public key, so every replica configured with the same key
 * produces the same kid, and a retired key keeps the kid its old tokens carry.
 */
@Component
public class JwtKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyStore.class);
    private static final int MIN_KEY_BITS = 2048;

    private final PrivateKey signingKey;
    private final RSAPublicKey currentPublicKey;
    private final String currentKeyId;
    private final Map<String, RSAPublicKey> verificationKeys = new LinkedHashMap<>();

    public JwtKeyStore(JwtProperties props) {
        RSAPrivateCrtKey privateKey;
        if (hasText(props.privateKey())) {
            privateKey = parsePrivateKey(props.privateKey());
        } else if (props.allowEphemeralKey()) {
            privateKey = generateEphemeralKey();
            log.warn("jwt.allow-ephemeral-key is ON: using a throw-away signing key. Tokens become invalid "
                    + "on restart and differ between instances. NEVER use this in production.");
        } else {
            throw new IllegalStateException(
                    "jwt.private-key is required. Generate one with scripts/generate-jwt-key.sh and set JWT_PRIVATE_KEY.");
        }
        requireStrong(privateKey.getModulus());

        this.signingKey = privateKey;
        this.currentPublicKey = derivePublicKey(privateKey);
        this.currentKeyId = keyId(currentPublicKey);
        this.verificationKeys.put(currentKeyId, currentPublicKey);

        List<String> previous = props.previousPublicKeys() == null ? List.of() : props.previousPublicKeys();
        for (String encoded : previous) {
            if (!hasText(encoded)) {
                continue;
            }
            RSAPublicKey key = parsePublicKey(encoded);
            requireStrong(key.getModulus());
            this.verificationKeys.putIfAbsent(keyId(key), key);
        }
        log.info("JWT signing key ready (kid={}, verification keys={})", currentKeyId, verificationKeys.size());
    }

    public PrivateKey signingKey() {
        return signingKey;
    }

    public String currentKeyId() {
        return currentKeyId;
    }

    public RSAPublicKey currentPublicKey() {
        return currentPublicKey;
    }

    /** Public key for a token's "kid" header, or null if unknown. */
    public RSAPublicKey verificationKey(String kid) {
        return kid == null ? null : verificationKeys.get(kid);
    }

    /** RFC 7517 JSON Web Key Set containing the current and any previous public keys. */
    public Map<String, Object> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        verificationKeys.forEach((kid, key) -> {
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("use", "sig");
            jwk.put("alg", "RS256");
            jwk.put("kid", kid);
            jwk.put("n", base64Url(key.getModulus()));
            jwk.put("e", base64Url(key.getPublicExponent()));
            keys.add(jwk);
        });
        return Map.of("keys", keys);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static void requireStrong(BigInteger modulus) {
        if (modulus.bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("RSA keys must be at least " + MIN_KEY_BITS + " bits");
        }
    }

    private static RSAPrivateCrtKey generateEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return (RSAPrivateCrtKey) pair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private static RSAPrivateCrtKey parsePrivateKey(String value) {
        if (value.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("jwt.private-key must be PKCS#8 ('BEGIN PRIVATE KEY'), not PKCS#1. "
                    + "Convert it: openssl pkcs8 -topk8 -nocrypt -in key.pem");
        }
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(value));
            Object key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (key instanceof RSAPrivateCrtKey crt) {
                return crt;
            }
            throw new IllegalStateException("jwt.private-key must be an RSA CRT private key");
        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("jwt.private-key is not a valid PKCS#8 RSA private key "
                    + "(PEM or Base64 DER)", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String value) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(value));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException
                 | ClassCastException e) {
            throw new IllegalStateException("jwt.previous-public-keys contains an invalid X.509 RSA public key", e);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot derive public key", e);
        }
    }

    /** Accepts PEM text, PEM with literal "\n" sequences (common in env vars) or bare Base64. */
    private static String stripPem(String value) {
        return value
                .replaceAll("-----[A-Z ]+-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
    }

    static String keyId(RSAPublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length); // drop sign byte: JWK uses unsigned big-endian
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
