package com.shopsphere.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.JsonNode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

/** What downstream services (and the gateway) rely on: a public JWKS, and tokens that verify against it. */
class JwksIntegrationTest extends ApiTestSupport {

    @Test
    void jwks_isPublic_cacheable_andListsAnRsaSigningKey() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=300")));

        JsonNode jwk = body(mvc.perform(get("/.well-known/jwks.json"))).get("keys").get(0);
        assertEquals("RSA", jwk.get("kty").asText());
        assertEquals("RS256", jwk.get("alg").asText());
        assertEquals("sig", jwk.get("use").asText());
    }

    @Test
    void accessToken_verifiesAgainstThePublishedKey_withExpectedClaims() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        JsonNode jwk = body(mvc.perform(get("/.well-known/jwks.json"))).get("keys").get(0);
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n").asText()));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e").asText()));
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));

        Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).requireIssuer("test-issuer").build()
                .parseSignedClaims(access);

        assertEquals(jwk.get("kid").asText(), jws.getHeader().getKeyId());
        assertTrue(jws.getPayload().getAudience().contains("shopsphere-api"));
        assertEquals("USER", jws.getPayload().get("role", String.class));
        Long.parseLong(jws.getPayload().getSubject()); // subject is the numeric user id
    }
}
