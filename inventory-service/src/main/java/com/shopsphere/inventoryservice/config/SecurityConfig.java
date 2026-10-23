package com.shopsphere.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopsphere.inventoryservice.security.JwtAuthenticationFilter;
import com.shopsphere.inventoryservice.security.ServiceTokenAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.serviceTokenAuthenticationFilter =
                serviceTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**")
                    .permitAll()

                // Service identity is required for stock mutations.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/inventory/*/reduce",
                    "/api/inventory/*/restore")
                    .hasRole("SERVICE")

                // Initialization remains ADMIN-only.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/inventory/**")
                    .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/inventory/**")
                    .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/inventory/**")
                    .hasRole("ADMIN")

                // Reads still require a valid user JWT.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/inventory/**")
                    .authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(
                serviceTokenAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
