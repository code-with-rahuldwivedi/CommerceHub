package com.shopsphere.userservice.security;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shopsphere.userservice.config.JwtProperties;
import com.shopsphere.userservice.entity.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;

/**
 * Issues RS256 access tokens and verifies them (with the same rules downstream services must apply):
 * signature by a known kid, algorithm RS256, issuer, audience and expiry.
 */
@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";
    private static final String ALGORITHM = "RS256";

    private final JwtProperties props;
    private final JwtKeyStore keys;
    private final JwtParser parser;

    public JwtService(JwtProperties props, JwtKeyStore keys) {
        this.props = props;
        this.keys = keys;
        // JwtParser is immutable and thread-safe: build once. The key is looked up by the token's kid.
        this.parser = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        return keys.verificationKey(header.getKeyId());
                    }
                })
                .requireIssuer(props.issuer())
                .clockSkewSeconds(30)
                .build();
    }

    public String generateAccessToken(long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keys.currentKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .audience().add(props.audience()).and()
                .subject(Long.toString(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(keys.signingKey(), Jwts.SIG.RS256)
                .compact();
    }

    /** Empty if the token is malformed, tampered, expired, unknown kid, wrong algorithm/issuer/audience. */
    public Optional<Claims> parse(String token) {
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(props.audience())) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
