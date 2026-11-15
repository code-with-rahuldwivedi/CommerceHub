package com.shopsphere.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.cloud.config.GatewayProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RateLimitFilterTest {

    private final AtomicLong clock = new AtomicLong();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitFilter filter(int auth, int anon, int user) {
        GatewayProperties.RateLimit props = new GatewayProperties.RateLimit(true, auth, anon, user, 1000);
        return new RateLimitFilter(props, new ProblemWriter(new ObjectMapper()), new SimpleMeterRegistry(),
                clock::get);
    }

    private MockHttpServletResponse call(RateLimitFilter filter, String method, String uri, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void signInAs(String userId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(userId).claim("role", "USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void authEndpointsAreLimitedPerIp() throws Exception {
        RateLimitFilter f = filter(2, 100, 100);

        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        assertThat(limited.getContentType()).contains("problem+json");

        // another client is not affected
        assertThat(call(f, "POST", "/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void bucketRefillsOverTime() throws Exception {
        RateLimitFilter f = filter(2, 100, 100);
        call(f, "POST", "/api/auth/login", "1.1.1.1");
        call(f, "POST", "/api/auth/login", "1.1.1.1");
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);

        clock.addAndGet(TimeUnit.SECONDS.toNanos(31)); // 2 per minute = one token every 30 s
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousReadsAreLimitedPerIp() throws Exception {
        RateLimitFilter f = filter(100, 1, 100);
        assertThat(call(f, "GET", "/api/products", "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(call(f, "GET", "/api/products", "3.3.3.3").getStatus()).isEqualTo(429);
    }

    @Test
    void signedInUsersAreLimitedPerUserNotPerIp() throws Exception {
        RateLimitFilter f = filter(100, 100, 1);

        signInAs("7");
        assertThat(call(f, "GET", "/api/orders", "4.4.4.4").getStatus()).isEqualTo(200);
        assertThat(call(f, "GET", "/api/orders", "4.4.4.4").getStatus()).isEqualTo(429);

        signInAs("8"); // same IP (shared NAT), different user: own bucket
        assertThat(call(f, "GET", "/api/orders", "4.4.4.4").getStatus()).isEqualTo(200);
    }

    @Test
    void actuatorIsNeverRateLimited() throws Exception {
        RateLimitFilter f = filter(1, 1, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(call(f, "GET", "/actuator/health", "5.5.5.5").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        GatewayProperties.RateLimit off = new GatewayProperties.RateLimit(false, 1, 1, 1, 1000);
        RateLimitFilter f = new RateLimitFilter(off, new ProblemWriter(new ObjectMapper()),
                new SimpleMeterRegistry(), clock::get);
        for (int i = 0; i < 5; i++) {
            assertThat(call(f, "POST", "/api/auth/login", "6.6.6.6").getStatus()).isEqualTo(200);
        }
    }
}
