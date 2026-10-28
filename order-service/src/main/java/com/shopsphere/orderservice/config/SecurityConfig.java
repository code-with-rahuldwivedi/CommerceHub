package com.shopsphere.orderservice.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopsphere.orderservice.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            // Disable CSRF for the stateless REST API
            .csrf(csrf -> csrf.disable())

            // JWT-based authentication; no HTTP session
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Handle authentication and authorization failures
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");

                    response.getWriter().write(
                        "{\"status\":403,"
                        + "\"error\":\"Forbidden\","
                        + "\"message\":\"You do not have permission "
                        + "to access this resource\"}"
                    );
                })
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth

                // Permit internal error dispatches
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()

                // Public actuator endpoints
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()

                // Only USER and ADMIN can create or access order POST routes
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/orders",
                    "/api/orders/**"
                ).hasAnyRole("USER", "ADMIN")

                // Deny all other requests by default
                .anyRequest().denyAll()
            )

            // Execute JWT authentication before username/password auth
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
