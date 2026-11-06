
package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.service.AuthMetrics;

import jakarta.servlet.http.HttpServletRequest;

class RateLimitFilterTest {

    private AppProperties.RateLimit props;
    private RateLimitFilter filter;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        props = new AppProperties.RateLimit(true, 3);
        filter = new RateLimitFilter(
                props,
                mock(ProblemDetailWriter.class),
                mock(AuthMetrics.class)
        );
        request = mock(HttpServletRequest.class);
    }

    @Test
    void shouldFilter_whenEnabledPostLoginRequest() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_whenRateLimitDisabled() {
        RateLimitFilter disabledFilter = new RateLimitFilter(
                new AppProperties.RateLimit(false, 3),
                mock(ProblemDetailWriter.class),
                mock(AuthMetrics.class)
        );

        assertTrue(disabledFilter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_whenMethodIsNotPost() {
        when(request.getMethod()).thenReturn("GET");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_whenPathIsNotLimited() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/users/profile");

        assertTrue(filter.shouldNotFilter(request));
    }
}
