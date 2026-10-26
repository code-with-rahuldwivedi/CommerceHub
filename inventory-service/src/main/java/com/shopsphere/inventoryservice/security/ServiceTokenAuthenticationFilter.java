package com.shopsphere.inventoryservice.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String SERVICE_HEADER = "X-Service-Token";

    private final byte[] expectedToken;

    public ServiceTokenAuthenticationFilter(
            @Value("${service.auth.token}") String serviceToken) {

        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException(
                    "INVENTORY_SERVICE_TOKEN must be configured");
        }

        this.expectedToken = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        boolean stockWriteEndpoint =
                path.matches("/api/inventory/[0-9]+/(reduce|restore)");

        return !HttpMethod.POST.matches(request.getMethod())
                || !stockWriteEndpoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String suppliedToken = request.getHeader(SERVICE_HEADER);

        if (suppliedToken == null || suppliedToken.isBlank()) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Service credentials are required");
            return;
        }

        boolean valid = MessageDigest.isEqual(
                expectedToken,
                suppliedToken.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid service credentials");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "order-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
