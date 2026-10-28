package com.shopsphere.orderservice.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || header.isBlank()) {
            log.debug("JWT_MISSING | method={} | uri={}", method, uri);
            filterChain.doFilter(request, response);
            return;
        }

        if (!header.startsWith("Bearer ")
                || header.substring(7).isBlank()) {

            log.warn("JWT_MALFORMED_HEADER | method={} | uri={}",
                    method, uri);

            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid Authorization header");
            return;
        }

        try {
            String token = header.substring(7).trim();
            Claims claims = jwtService.parse(token);

            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            Object rawUserId = claims.get("userId");

            Long userId = rawUserId instanceof Number number
                    ? number.longValue()
                    : rawUserId instanceof String value
                            ? Long.parseLong(value)
                            : null;

            boolean validClaims =
                    subject != null
                    && !subject.isBlank()
                    && userId != null
                    && userId > 0
                    && ("USER".equals(role) || "ADMIN".equals(role));

            if (!validClaims) {
                log.warn(
                        "JWT_INVALID_CLAIMS | method={} | uri={} | subjectPresent={} | userIdPresent={} | roleValid={}",
                        method,
                        uri,
                        subject != null && !subject.isBlank(),
                        userId != null && userId > 0,
                        "USER".equals(role) || "ADMIN".equals(role));

                SecurityContextHolder.clearContext();
                response.sendError(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Invalid token claims");
                return;
            }

            var authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_" + role)));

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            log.debug(
                    "JWT_AUTHENTICATED | method={} | uri={} | userId={} | role={}",
                    method, uri, userId, role);

        } catch (Exception ex) {
            log.warn(
                    "JWT_VALIDATION_FAILED | method={} | uri={} | exceptionType={}",
                    method, uri, ex.getClass().getSimpleName());

            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
