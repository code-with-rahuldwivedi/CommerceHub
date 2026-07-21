package com.shopsphere.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopsphere.productservice.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                    // Public GET APIs (Order Service Feign call bhi yahin se aayega)
                    .requestMatchers(HttpMethod.GET, "/api/product/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/getAllProducts").permitAll()

                    // Admin Only
                    .requestMatchers(HttpMethod.POST, "/api/createProduct").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/product/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/product/**").hasRole("ADMIN")

                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}