package com.shopsphere.productservice;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for user-service in tests: serves a real JWKS over HTTP and mints tokens
 * the same way user-service does (RS256, kid, iss, aud, numeric sub, role claim).
 */
public final class JwksTestServer {

    public static final String KID = "test-key-1";
    public static final String ISSUER = "test-issuer";
    public static final String AUDIENCE = "shopsphere-api";

    private static final RSAKey SIGNING_KEY;
    private static final RSAKey UNPUBLISHED_KEY;
    private static final RSAKey IMPOSTOR_KEY;
    private static final HttpServer SERVER;

    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID(KID).generate();
            UNPUBLISHED_KEY = new RSAKeyGenerator(2048).keyID("not-in-jwks").generate();
            IMPOSTOR_KEY = new RSAKeyGenerator(2048).keyID(KID).generate(); // same kid, different key material

            byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            SERVER = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            SERVER.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(jwks);
                }
            });
            SERVER.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwksTestServer() {
    }

    public static String jwksUri() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/.well-known/jwks.json";
    }

    // ------------------------------------------------------------------ valid tokens

    public static String adminToken() {
        return token("1", "ADMIN");
    }

    public static String userToken() {
        return token("2", "USER");
    }

    public static String token(String subject, String role) {
        return sign(SIGNING_KEY, claims(subject, role, ISSUER, AUDIENCE, Instant.now().plusSeconds(600)));
    }

    // ------------------------------------------------------------------ deliberately bad tokens

    public static String expiredToken() {
        return sign(SIGNING_KEY, claims("1", "ADMIN", ISSUER, AUDIENCE, Instant.now().minusSeconds(600)));
    }

    public static String tokenWithIssuer(String issuer) {
        return sign(SIGNING_KEY, claims("1", "ADMIN", issuer, AUDIENCE, Instant.now().plusSeconds(600)));
    }

    public static String tokenWithAudience(String audience) {
        return sign(SIGNING_KEY, claims("1", "ADMIN", ISSUER, audience, Instant.now().plusSeconds(600)));
    }

    public static String tokenWithoutRole() {
        return sign(SIGNING_KEY, new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject("1")
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
    }

    public static String tokenSignedWithUnpublishedKey() {
        return sign(UNPUBLISHED_KEY, claims("1", "ADMIN", ISSUER, AUDIENCE, Instant.now().plusSeconds(600)));
    }

    /** Right kid, wrong key material: the signature must not verify. */
    public static String tokenSignedByImpostor() {
        return sign(IMPOSTOR_KEY, claims("1", "ADMIN", ISSUER, AUDIENCE, Instant.now().plusSeconds(600)));
    }

    /** Algorithm-confusion attempt: HS256 token. */
    public static String hmacToken() {
        try {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).build(),
                    claims("1", "ADMIN", ISSUER, AUDIENCE, Instant.now().plusSeconds(600)));
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String unsignedToken() {
        return new PlainJWT(claims("1", "ADMIN", ISSUER, AUDIENCE, Instant.now().plusSeconds(600))).serialize();
    }

    // ------------------------------------------------------------------ internals

    private static JWTClaimsSet claims(String subject, String role, String issuer, String audience, Instant expires) {
        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("role", role)
                .issueTime(new Date())
                .expirationTime(Date.from(expires))
                .build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
