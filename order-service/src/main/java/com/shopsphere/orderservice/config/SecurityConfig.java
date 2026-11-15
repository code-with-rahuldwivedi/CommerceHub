package com.shopsphere.orderservice.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

import com.shopsphere.orderservice.security.JwtDecoderFactory;
import com.shopsphere.orderservice.security.ProblemDetailWriter;
import com.shopsphere.orderservice.security.RequestSizeLimitFilter;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    private static final String INTERNAL_NETWORKS_ONLY =
            "hasIpAddress('127.0.0.1') or hasIpAddress('::1') or hasIpAddress('10.0.0.0/8') "
                    + "or hasIpAddress('172.16.0.0/12') or hasIpAddress('192.168.0.0/16')";

    @Bean
    public JwtDecoder jwtDecoder(SecurityJwtProperties properties) {
        return JwtDecoderFactory.create(properties);
    }

    /** Maps the "role" claim (USER / ADMIN) to ROLE_USER / ROLE_ADMIN. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtDecoder jwtDecoder,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   AppProperties appProperties,
                                                   ProblemDetailWriter problemWriter) throws Exception {

        RequestSizeLimitFilter sizeFilter =
                new RequestSizeLimitFilter(appProperties.request().maxBodyBytes(), problemWriter);

        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .requestMatchers(EndpointRequest.to("info", "prometheus"))
                        .access(new WebExpressionAuthorizationManager(INTERNAL_NETWORKS_ONLY))

                        // Swagger & OpenAPI endpoints grouped cleanly together
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Protected API endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/orders", "/api/orders/**").hasAnyRole("USER", "ADMIN")

                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, e) -> {
                            bearerEntryPoint.commence(request, response, e);
                            problemWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            bearerDeniedHandler.handle(request, response, e);
                            problemWriter.write(response, HttpStatus.FORBIDDEN, "Access denied");
                        }))
                .addFilterBefore(sizeFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}