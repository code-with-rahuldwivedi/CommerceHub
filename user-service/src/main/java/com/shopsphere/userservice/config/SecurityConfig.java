package com.shopsphere.userservice.config;

import com.shopsphere.userservice.security.RateLimitFilter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shopsphere.userservice.security.JwtAuthenticationFilter;
import com.shopsphere.userservice.security.JwtService;
import com.shopsphere.userservice.security.ProblemDetailWriter;
import com.shopsphere.userservice.security.RequestSizeLimitFilter;
import com.shopsphere.userservice.service.AuthMetrics;

@Configuration
public class SecurityConfig {

    /**
     * /actuator/info and /actuator/prometheus are reachable only from loopback and
     * private networks. Always ALSO block them at the ingress/gateway.
     */
    private static final String INTERNAL_NETWORKS_ONLY =
            "hasIpAddress('127.0.0.1') or hasIpAddress('::1') or hasIpAddress('10.0.0.0/8') "
          + "or hasIpAddress('172.16.0.0/12') or hasIpAddress('192.168.0.0/16')";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Spring wires a DaoAuthenticationProvider from the CustomUserDetailsService and
     * PasswordEncoder beans. It also gives us constant-time handling of unknown users.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   AppProperties appProperties,
                                                   ProblemDetailWriter problemWriter,
                                                   AuthMetrics metrics) throws Exception {

        // Created here (not as @Component beans) so Boot does not also register them
        // as plain servlet filters outside the security chain.
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(appProperties.rateLimit(), problemWriter, metrics);
        RequestSizeLimitFilter sizeFilter =
                new RequestSizeLimitFilter(appProperties.request().maxBodyBytes(), problemWriter);

        http
            .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .requestMatchers(EndpointRequest.to("info", "prometheus"))
                    .access(new WebExpressionAuthorizationManager(INTERNAL_NETWORKS_ONLY))
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
                        "/api/auth/verify-email",
                        "/api/auth/resend-verification").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    problemWriter.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
                })
                .accessDeniedHandler((request, response, e) ->
                    problemWriter.write(response, HttpStatus.FORBIDDEN, "Access denied")))
            .addFilterBefore(sizeFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
