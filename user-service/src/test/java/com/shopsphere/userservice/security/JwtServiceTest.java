package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.shopsphere.userservice.config.JwtProperties;
import com.shopsphere.userservice.entity.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String ISSUER = "svc";
    private static final String AUDIENCE = "api";

    // ---------- helpers ----------

    private static KeyPair newKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String b64(byte[] der) {
        return Base64.getEncoder().encodeToString(der);
    }

    private static JwtProperties props(String privateKey, List<String> previous, boolean ephemeral,
                                       String issuer, String audience, Duration accessTtl) {
        return new JwtProperties(privateKey, previous, ephemeral, issuer, audience, accessTtl, Duration.ofDays(7));
    }

    private static JwtKeyStore ephemeralStore() {
        return new JwtKeyStore(props(null, List.of(), true, ISSUER, AUDIENCE, Duration.ofMinutes(15)));
    }

    private static JwtService service(JwtKeyStore store, String issuer, String audience, Duration ttl) {
        return new JwtService(props(null, List.of(), true, issuer, audience, ttl), store);
    }

    private static JwtService service(JwtKeyStore store) {
        return service(store, ISSUER, AUDIENCE, Duration.ofMinutes(15));
    }

    // ---------- tokens ----------

    @Test
    void validToken_carriesExpectedClaimsAndHeader() {
        JwtKeyStore store = ephemeralStore();
        JwtService jwt = service(store);

        String token = jwt.generateAccessToken(42L, Role.ADMIN);
        Optional<Claims> claims = jwt.parse(token);

        assertTrue(claims.isPresent());
        assertEquals("42", claims.get().getSubject());
        assertEquals("ADMIN", claims.get().get(JwtService.ROLE_CLAIM, String.class));
        assertEquals(ISSUER, claims.get().getIssuer());
        assertTrue(claims.get().getAudience().contains(AUDIENCE));
        assertEquals(900L, jwt.accessTtlSeconds());
    }

    @Test
    void everyTokenGetsAUniqueId() {
        JwtService jwt = service(ephemeralStore());

        String first = jwt.parse(jwt.generateAccessToken(1L, Role.USER)).orElseThrow().getId();
        String second = jwt.parse(jwt.generateAccessToken(1L, Role.USER)).orElseThrow().getId();

        assertNotEquals(first, second);
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwt = service(ephemeralStore(), ISSUER, AUDIENCE, Duration.ofSeconds(-120)); // beyond clock skew

        assertTrue(jwt.parse(jwt.generateAccessToken(1L, Role.USER)).isEmpty());
    }

    @Test
    void tamperedSignature_isRejected() {
        JwtService jwt = service(ephemeralStore());
        String token = jwt.generateAccessToken(1L, Role.USER);

        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertTrue(jwt.parse(tampered).isEmpty());
    }

    @Test
    void tokenFromAnotherKey_isRejected() {
        JwtService mine = service(ephemeralStore());
        JwtService foreign = service(ephemeralStore());

        assertTrue(mine.parse(foreign.generateAccessToken(1L, Role.ADMIN)).isEmpty());
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() {
        JwtKeyStore store = ephemeralStore();
        JwtService other = service(store, "someone-else", AUDIENCE, Duration.ofMinutes(15));

        assertTrue(service(store).parse(other.generateAccessToken(1L, Role.USER)).isEmpty());
    }

    @Test
    void tokenForAnotherAudience_isRejected() {
        JwtKeyStore store = ephemeralStore();
        JwtService other = service(store, ISSUER, "some-other-api", Duration.ofMinutes(15));

        assertTrue(service(store).parse(other.generateAccessToken(1L, Role.USER)).isEmpty());
    }

    @Test
    void garbageInput_isRejected() {
        JwtService jwt = service(ephemeralStore());

        assertTrue(jwt.parse("not-a-jwt").isEmpty());
        assertTrue(jwt.parse("").isEmpty());
    }

    @Test
    void unsignedToken_isRejected() {
        JwtService jwt = service(ephemeralStore());
        String unsigned = Jwts.builder().subject("1").issuer(ISSUER).compact();

        assertTrue(jwt.parse(unsigned).isEmpty());
    }

    @Test
    void hmacTokenSignedWithThePublicKey_isRejected() {
        // classic algorithm-confusion attack: HS256/512 token using the public key as the HMAC secret
        JwtKeyStore store = ephemeralStore();
        JwtService jwt = service(store);
        SecretKey hmacKey = Keys.hmacShaKeyFor(store.currentPublicKey().getEncoded());

        String forged = Jwts.builder()
                .header().keyId(store.currentKeyId()).and()
                .subject("1").issuer(ISSUER).audience().add(AUDIENCE).and()
                .claim(JwtService.ROLE_CLAIM, "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(hmacKey)
                .compact();

        assertTrue(jwt.parse(forged).isEmpty());
    }

    // ---------- key handling ----------

    @Test
    void jwks_publishesAKeyThatVerifiesIssuedTokens() throws Exception {
        JwtKeyStore store = ephemeralStore();
        JwtService jwt = service(store);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) store.jwks().get("keys");
        assertEquals(1, keys.size());
        Map<String, Object> jwk = keys.get(0);
        assertEquals("RSA", jwk.get("kty"));
        assertEquals("RS256", jwk.get("alg"));
        assertEquals("sig", jwk.get("use"));
        assertEquals(store.currentKeyId(), jwk.get("kid"));

        // rebuild the public key purely from the published n / e and verify with it
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n")));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")));
        RSAPublicKey rebuilt = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n, e));

        Jws<Claims> jws = Jwts.parser().verifyWith(rebuilt).build()
                .parseSignedClaims(jwt.generateAccessToken(7L, Role.USER));
        assertEquals("7", jws.getPayload().getSubject());
        assertEquals(jwk.get("kid"), jws.getHeader().getKeyId());
    }

    @Test
    void rotation_previousKeyStillVerifiesOldTokens_andStaysInJwks() throws Exception {
        KeyPair oldPair = newKeyPair(2048);
        KeyPair newPair = newKeyPair(2048);

        JwtKeyStore oldStore = new JwtKeyStore(props(b64(oldPair.getPrivate().getEncoded()), List.of(), false,
                ISSUER, AUDIENCE, Duration.ofMinutes(15)));
        JwtKeyStore newStore = new JwtKeyStore(props(b64(newPair.getPrivate().getEncoded()),
                List.of(b64(oldPair.getPublic().getEncoded())), false, ISSUER, AUDIENCE, Duration.ofMinutes(15)));

        String tokenSignedWithOldKey = service(oldStore).generateAccessToken(1L, Role.USER);
        String tokenSignedWithNewKey = service(newStore).generateAccessToken(1L, Role.USER);

        assertTrue(service(newStore).parse(tokenSignedWithOldKey).isPresent());
        assertTrue(service(newStore).parse(tokenSignedWithNewKey).isPresent());
        assertTrue(service(oldStore).parse(tokenSignedWithNewKey).isEmpty()); // old instance doesn't know the new key
        assertNotEquals(oldStore.currentKeyId(), newStore.currentKeyId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) newStore.jwks().get("keys");
        assertEquals(2, keys.size());
        assertEquals(newStore.currentKeyId(), keys.get(0).get("kid")); // current key first
    }

    @Test
    void sameKeyGivesSameKid_acrossReplicas_andAcceptsPemFormats() throws Exception {
        KeyPair pair = newKeyPair(2048);
        String der = b64(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        String pemWithEscapedNewlines = pem.replace("\n", "\\n"); // how env vars often carry PEM

        String kid1 = new JwtKeyStore(props(der, List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))).currentKeyId();
        String kid2 = new JwtKeyStore(props(pem, List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))).currentKeyId();
        String kid3 = new JwtKeyStore(props(pemWithEscapedNewlines, List.of(), false, ISSUER, AUDIENCE,
                Duration.ofMinutes(1))).currentKeyId();

        assertEquals(kid1, kid2);
        assertEquals(kid1, kid3);
    }

    @Test
    void missingKey_withoutEphemeralFlag_failsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtKeyStore(props("", List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))));
    }

    @Test
    void weakKey_failsAtStartup() throws Exception {
        String weak = b64(newKeyPair(1024).getPrivate().getEncoded());

        assertThrows(IllegalStateException.class,
                () -> new JwtKeyStore(props(weak, List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))));
    }

    @Test
    void garbageKey_failsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtKeyStore(props("not a key", List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))));
    }

    @Test
    void pkcs1Key_isRejectedWithHelpfulError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new JwtKeyStore(props(
                "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----",
                List.of(), false, ISSUER, AUDIENCE, Duration.ofMinutes(1))));

        assertTrue(e.getMessage().contains("PKCS#8"));
    }
    
    @Test
    void tokenWithoutAudience_isRejected() {
        JwtKeyStore store = ephemeralStore();
        JwtService jwt = service(store);

        String token = Jwts.builder()
                .header().keyId(store.currentKeyId()).and()
                .issuer(ISSUER)
                .subject("1")
                .claim(JwtService.ROLE_CLAIM, "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(store.signingKey(), Jwts.SIG.RS256)
                .compact();

        assertTrue(jwt.parse(token).isEmpty());
    }
    
    @Test
    void tokenSignedWithRs384_isRejected() {
        JwtKeyStore store = ephemeralStore();
        JwtService jwt = service(store);

        String token = Jwts.builder()
                .header().keyId(store.currentKeyId()).and()
                .subject("1")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim(JwtService.ROLE_CLAIM, "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(store.signingKey(), Jwts.SIG.RS384)
                .compact();

        assertTrue(jwt.parse(token).isEmpty());
    }
}
