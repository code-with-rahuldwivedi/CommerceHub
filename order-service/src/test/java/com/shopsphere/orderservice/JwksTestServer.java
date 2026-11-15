package com.shopsphere.orderservice;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class JwksTestServer {

    private static final String KEY_ID = "shopsphere-test-key";

    private static final HttpServer SERVER;

    private static final String JWKS_URI;

    static {
        try {
            // Generate RSA key pair
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            KeyPair keyPair = generator.generateKeyPair();

            RSAPublicKey publicKey =
                    (RSAPublicKey) keyPair.getPublic();

            // Convert RSA public key parameters to Base64URL
            String modulus = encode(publicKey.getModulus());

            String exponent = encode(publicKey.getPublicExponent());

            // Build JWKS JSON
            String jwksJson = """
                    {
                      "keys": [
                        {
                          "kty": "RSA",
                          "use": "sig",
                          "kid": "%s",
                          "alg": "RS256",
                          "n": "%s",
                          "e": "%s"
                        }
                      ]
                    }
                    """.formatted(KEY_ID, modulus, exponent);

            // Start local HTTP server on a random port
            SERVER = HttpServer.create(
                    new InetSocketAddress("localhost", 0),
                    0
            );

            SERVER.createContext("/.well-known/jwks.json", exchange -> {

                byte[] response = jwksJson.getBytes(
                        StandardCharsets.UTF_8
                );

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json"
                );

                exchange.sendResponseHeaders(200, response.length);

                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });

            SERVER.start();

            JWKS_URI = "http://localhost:"
                    + SERVER.getAddress().getPort()
                    + "/.well-known/jwks.json";

        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private JwksTestServer() {
        // Utility class
    }

    public static String jwksUri() {
        return JWKS_URI;
    }

    private static String encode(BigInteger value) {

        byte[] bytes = value.toByteArray();

        // Remove leading sign byte if present
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(
                    bytes, 1, bytes.length
            );
        }

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
