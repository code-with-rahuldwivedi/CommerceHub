package com.shopsphere.productservice.config;

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

import com.shopsphere.productservice.security.JwtDecoderFactory;
import com.shopsphere.productservice.security.ProblemDetailWriter;
import com.shopsphere.productservice.security.RequestSizeLimitFilter;

@Configuration
public class SecurityConfig {

    /**
     * /actuator/info and /actuator/prometheus are reachable only from loopback and private
     * networks. Always ALSO block them at the ingress/gateway.
     */
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
            .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .requestMatchers(EndpointRequest.to("info", "prometheus"))
                    .access(new WebExpressionAuthorizationManager(INTERNAL_NETWORKS_ONLY))

                // ---- catalog: public reads ----
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/product/**", "/api/getAllProducts").permitAll()

                // ---- catalog: ADMIN writes ----
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/createProduct").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**", "/api/product/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/product/**").hasRole("ADMIN")

                .anyRequest().authenticated())
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
