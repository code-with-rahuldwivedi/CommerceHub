package com.shopsphere.cloud.config;

import java.util.List;

import com.shopsphere.cloud.web.RateLimitFilter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.cloud.security.JwtDecoderFactory;
import com.shopsphere.cloud.web.AccessLogFilter;
import com.shopsphere.cloud.web.ProblemWriter;
import com.shopsphere.cloud.web.RequestSizeLimitFilter;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;

/**
 * Edge security. Default is DENY: only the routes listed below are reachable. Every downstream service validates
 * the same token again (defence in depth); the gateway's job is to stop bad traffic early and to make sure that
 * internal-only endpoints can never be reached from outside.
 */
@Configuration
public class SecurityConfig {

    /** Management endpoints other than health: loopback / private networks only (also keep them on their own port). */
    private static final String INTERNAL_NETWORKS_ONLY =
            "hasIpAddress('127.0.0.1') or hasIpAddress('::1') or hasIpAddress('10.0.0.0/8') "
          + "or hasIpAddress('172.16.0.0/12') or hasIpAddress('192.168.0.0/16')";

    private static final String[] PUBLIC_AUTH_POSTS = {
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/auth/verify-email", "/api/auth/resend-verification"
    };

    private static final String[] PUBLIC_CATALOG_READS = {
            "/api/products", "/api/products/**", "/api/product/**", "/api/getAllProducts"
    };

    @Bean
    public ProblemWriter problemWriter(ObjectMapper objectMapper) {
        return new ProblemWriter(objectMapper);
    }

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
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        List<String> origins = properties.cors().allowedOrigins().stream()
                .map(String::trim).filter(o -> !o.isEmpty()).toList();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            return source; // no CORS configuration registered: browsers on other origins are refused
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "Idempotent-Replayed", "Location"));
        cors.setAllowCredentials(false); // bearer tokens travel in a header, never in cookies
        cors.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtDecoder jwtDecoder,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   GatewayProperties properties,
                                                   ProblemWriter problemWriter,
                                                   MeterRegistry meterRegistry) throws Exception {

        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();

        RequestSizeLimitFilter sizeFilter = new RequestSizeLimitFilter(
                properties.request().maxBodyBytes(), properties.request().rejectChunked(), problemWriter);
        AccessLogFilter accessLogFilter = new AccessLogFilter();
        RateLimitFilter rateLimitFilter = new RateLimitFilter(properties.rateLimit(), problemWriter, meterRegistry);

        http
            .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .authorizeHttpRequests(auth -> auth
                // The circuit-breaker fallback is an internal FORWARD; the error page is an internal ERROR dispatch.
                // (Direct client requests to /fallback/** are REQUEST dispatches and fall through to denyAll.)
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .requestMatchers(EndpointRequest.toAnyEndpoint())
                    .access(new WebExpressionAuthorizationManager(INTERNAL_NETWORKS_ONLY))

                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll() // CORS preflight

                // ---- public: authentication flows and catalog reads ----
                .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POSTS).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_CATALOG_READS).permitAll()

                // ---- inventory: stock mutation is a service-to-service operation, NEVER reachable from outside ----
                .requestMatchers("/api/inventory/*/reduce", "/api/inventory/*/restore").denyAll()
                .requestMatchers("/api/inventory", "/api/inventory/**").hasRole("ADMIN")

                // ---- back office ----
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/createProduct").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**", "/api/product/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**", "/api/product/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/product/**").hasRole("ADMIN")

                // ---- signed-in customers ----
                .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/orders", "/api/orders/**").hasAnyRole("USER", "ADMIN")

                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    bearerEntryPoint.commence(request, response, e);
                    problemWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
                })
                .accessDeniedHandler((request, response, e) -> {
                    bearerDeniedHandler.handle(request, response, e);
                    problemWriter.write(response, HttpStatus.FORBIDDEN, "Access denied");
                }))
            .addFilterBefore(sizeFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(accessLogFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, AccessLogFilter.class);

        return http.build();
    }
}
