package com.shopsphere.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.orderservice.config.SecurityJwtProperties;

import jakarta.servlet.FilterChain;

class SecurityComponentsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void correlationIdFilter_acceptsValidHeaderOrGeneratesUuidAndCleansMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest validReq = new MockHttpServletRequest();
        validReq.addHeader("X-Request-Id", "valid_req-1234");
        filter.doFilter(validReq, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo("valid_req-1234"));
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("valid_req-1234");
        assertThat(MDC.get("requestId")).isNull();

        MockHttpServletRequest badReq = new MockHttpServletRequest();
        badReq.addHeader("X-Request-Id", "bad\nid");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(badReq, res2, new MockFilterChain());
        assertThat(res2.getHeader("X-Request-Id")).hasSize(36);
    }

    @Test
    void requestSizeLimitFilter_blocksOversizedRequestsWithProblemDetail() throws Exception {
        ProblemDetailWriter writer = new ProblemDetailWriter(new ObjectMapper());
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(1024, writer);

        MockHttpServletRequest bigReq = new MockHttpServletRequest();
        bigReq.setContent(new byte[2048]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(bigReq, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(res.getContentAsString()).contains("Request body too large");
        verify(chain, never()).doFilter(bigReq, res);

        MockHttpServletRequest okReq = new MockHttpServletRequest();
        okReq.setContent(new byte[512]);
        MockHttpServletResponse okRes = new MockHttpServletResponse();
        filter.doFilter(okReq, okRes, chain);
        verify(chain).doFilter(okReq, okRes);
    }

    @Test
    void currentUser_extractsUserIdAndAdminRoleFromSecurityContext() {
        assertThat(CurrentUser.fromContext()).isEqualTo(CurrentUser.anonymous());

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(CurrentUser.fromContext()).isEqualTo(CurrentUser.anonymous());

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("123")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        CurrentUser user = CurrentUser.fromContext();
        assertThat(user.userId()).isEqualTo(123L);
        assertThat(user.admin()).isTrue();
    }

    @Test
    void jwtDecoderFactory_validatorEnforcesIssuerAudienceNumericSubAndRole() {
        OAuth2TokenValidator<Jwt> validator = JwtDecoderFactory.validator("shopsphere-user", "shopsphere-api");

        Jwt valid = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("shopsphere-user")
                .audience(List.of("shopsphere-api"))
                .subject("42")
                .claim("role", "USER")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThat(validator.validate(valid).hasErrors()).isFalse();

        Jwt badRole = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("shopsphere-user")
                .audience(List.of("shopsphere-api"))
                .subject("abc")
                .claim("role", "GUEST")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThat(validator.validate(badRole).hasErrors()).isTrue();

        assertThat(JwtDecoderFactory.create(new SecurityJwtProperties(
                "http://localhost:8081/.well-known/jwks.json", "shopsphere-user", "shopsphere-api",
                Duration.ofSeconds(2), Duration.ofSeconds(2)))).isNotNull();
    }
}
