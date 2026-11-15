package com.shopsphere.cloud.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtDecoderFactoryTest {

    private final OAuth2TokenValidator<Jwt> validator = JwtDecoderFactory.validator("iss", "aud");

    private static Jwt.Builder valid() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("iss").audience(List.of("aud")).subject("42").claim("role", "USER")
                .issuedAt(now.minusSeconds(10)).expiresAt(now.plusSeconds(300));
    }

    @Test
    void acceptsAWellFormedToken() {
        assertThat(validator.validate(valid().build()).hasErrors()).isFalse();
        assertThat(validator.validate(valid().claim("role", "ADMIN").build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(valid().issuer("someone-else").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(valid().audience(List.of("other-api")).build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsNonNumericSubject() {
        assertThat(validator.validate(valid().subject("admin").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsUnknownRole() {
        assertThat(validator.validate(valid().claim("role", "SERVICE").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        Jwt expired = valid().issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        assertThat(validator.validate(expired).hasErrors()).isTrue();
    }
}
