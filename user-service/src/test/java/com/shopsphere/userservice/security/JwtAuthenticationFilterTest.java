package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.mock.web.MockFilterChain;

import com.shopsphere.userservice.entity.Role;

import io.jsonwebtoken.Claims;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeader_continuesWithoutAuthentication() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        assertNotNull(chain.getRequest());
    }

    @Test
    void nonBearerAuthorizationHeader_continuesWithoutAuthentication() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    void invalidToken_continuesWithoutAuthentication() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        when(jwtService.parse("invalid-token")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService).parse("invalid-token");
    }

    @Test
    void validToken_setsAuthenticationWithUserRole() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(claims.get(JwtService.ROLE_CLAIM, String.class)).thenReturn("USER");
        when(jwtService.parse("valid-token")).thenReturn(Optional.of(claims));

        filter.doFilter(request, response, chain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertInstanceOf(JwtPrincipal.class, authentication.getPrincipal());
        assertEquals("ROLE_USER",
                authentication.getAuthorities().iterator().next().getAuthority());

        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertEquals(42L, principal.userId());
        assertEquals(Role.USER, principal.role());
    }

    @Test
    void malformedUserId_doesNotSetAuthentication() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-subject");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("not-a-number");
        when(claims.get(JwtService.ROLE_CLAIM, String.class)).thenReturn("USER");
        when(jwtService.parse("bad-subject")).thenReturn(Optional.of(claims));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidRole_doesNotSetAuthentication() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-role");

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(claims.get(JwtService.ROLE_CLAIM, String.class)).thenReturn("UNKNOWN");
        when(jwtService.parse("bad-role")).thenReturn(Optional.of(claims));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void existingAuthentication_skipsJwtParsing() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken(
                "existing-user", null,
                java.util.List.of());

        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        filter.doFilter(request, response, chain);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
    }

    @Test
    void bearerTokenWithWhitespace_isTrimmedBeforeParsing() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   valid-token  ");

        when(jwtService.parse("valid-token")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        verify(jwtService).parse("valid-token");
    }
}
