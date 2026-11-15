package com.shopsphere.cloud.security;

import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import com.shopsphere.cloud.config.SecurityJwtProperties;

/**
 * Same rules as product-service and order-service: RS256 only, key chosen by "kid" from user-service's JWKS
 * (refreshed when an unknown kid shows up, so key rotation needs no restart), plus expiry, issuer, audience,
 * numeric subject and a USER/ADMIN role claim. The JWKS is fetched lazily, so the gateway starts even if
 * user-service is down.
 */
public final class JwtDecoderFactory {

    private JwtDecoderFactory() {
    }

    public static JwtDecoder create(SecurityJwtProperties properties) {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout())
                .build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(restTemplate)
                .build();
        decoder.setJwtValidator(validator(properties.issuer(), properties.audience()));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("sub", sub -> sub != null && sub.matches("\\d{1,18}")),
                new JwtClaimValidator<String>("role", role -> "USER".equals(role) || "ADMIN".equals(role)));
    }
}
