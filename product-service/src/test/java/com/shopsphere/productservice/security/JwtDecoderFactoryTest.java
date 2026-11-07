package com.shopsphere.productservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.shopsphere.productservice.JwksTestServer;
import com.shopsphere.productservice.config.SecurityJwtProperties;

/** The token rules every downstream service must enforce, tested against a real JWKS over HTTP. */
class JwtDecoderFactoryTest {

    private final JwtDecoder decoder = JwtDecoderFactory.create(new SecurityJwtProperties(
            JwksTestServer.jwksUri(), JwksTestServer.ISSUER, JwksTestServer.AUDIENCE,
            Duration.ofSeconds(2), Duration.ofSeconds(3)));

    private void assertRejected(String token) {
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void validToken_isAccepted_withSubjectAndRole() {
        Jwt jwt = decoder.decode(JwksTestServer.token("42", "ADMIN"));

        assertEquals("42", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
    }

    @Test
    void expiredToken_isRejected() {
        assertRejected(JwksTestServer.expiredToken());
    }

    @Test
    void wrongIssuer_isRejected() {
        assertRejected(JwksTestServer.tokenWithIssuer("someone-else"));
    }

    @Test
    void wrongAudience_isRejected() {
        assertRejected(JwksTestServer.tokenWithAudience("another-api"));
    }

    @Test
    void nonNumericSubject_isRejected() {
        assertRejected(JwksTestServer.token("admin@example.com", "ADMIN"));
    }

    @Test
    void unknownRole_isRejected() {
        assertRejected(JwksTestServer.token("1", "SUPERUSER"));
    }

    @Test
    void missingRole_isRejected() {
        assertRejected(JwksTestServer.tokenWithoutRole());
    }

    @Test
    void tokenSignedWithAKeyThatIsNotInTheJwks_isRejected() {
        assertRejected(JwksTestServer.tokenSignedWithUnpublishedKey());
    }

    @Test
    void tokenWithTheRightKidButForgedSignature_isRejected() {
        assertRejected(JwksTestServer.tokenSignedByImpostor());
    }

    @Test
    void hmacTokenUsingAnRsaKid_isRejected() {
        assertRejected(JwksTestServer.hmacToken());
    }

    @Test
    void unsignedToken_isRejected() {
        assertRejected(JwksTestServer.unsignedToken());
    }

    @Test
    void tamperedToken_isRejected() {
        String token = JwksTestServer.adminToken();
        String tampered = token.substring(0, token.length() - 6) + (token.endsWith("AAAAAA") ? "BBBBBB" : "AAAAAA");

        assertRejected(tampered);
    }

    @Test
    void garbage_isRejected() {
        assertRejected("not-a-jwt");
        assertRejected("a.b.c");
    }
}
