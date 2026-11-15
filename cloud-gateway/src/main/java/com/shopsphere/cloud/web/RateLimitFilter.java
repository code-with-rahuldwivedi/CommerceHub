package com.shopsphere.cloud.web;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shopsphere.cloud.config.GatewayProperties;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Three tiers, each with its own bucket:
 * <ul>
 *   <li><b>auth</b>: POST /api/auth/** per client IP (credential stuffing, mail bombing);</li>
 *   <li><b>user</b>: authenticated requests per user id (subject of the token);</li>
 *   <li><b>anon</b>: everything else per client IP.</li>
 * </ul>
 * The client IP is {@code getRemoteAddr()}: correct only if the container resolves X-Forwarded-For from trusted
 * proxies (server.forward-headers-strategy=native + server.tomcat.remoteip.internal-proxies, see application.yml).
 * Runs after the JWT filter so authenticated callers are limited per user, not per shared NAT address.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final GatewayProperties.RateLimit props;
    private final ProblemWriter problemWriter;
    private final MeterRegistry metrics;
    private final LongSupplier nanoClock;
    private final Cache<String, TokenBucket> buckets;

    public RateLimitFilter(GatewayProperties.RateLimit props, ProblemWriter problemWriter, MeterRegistry metrics) {
        this(props, problemWriter, metrics, System::nanoTime);
    }

    RateLimitFilter(GatewayProperties.RateLimit props, ProblemWriter problemWriter, MeterRegistry metrics,
                    LongSupplier nanoClock) {
        this.props = props;
        this.problemWriter = problemWriter;
        this.metrics = metrics;
        this.nanoClock = nanoClock;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(props.maxKeys())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.enabled() || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tier;
        String key;
        int limit;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if ("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/auth/")) {
            tier = "auth";
            key = "auth|" + request.getRemoteAddr();
            limit = props.authPerMinute();
        } else if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            tier = "user";
            key = "user|" + jwt.getToken().getSubject();
            limit = props.userPerMinute();
        } else {
            tier = "anon";
            key = "anon|" + request.getRemoteAddr();
            limit = props.anonymousPerMinute();
        }

        long now = nanoClock.getAsLong();
        long retryAfter = buckets.get(key, k -> new TokenBucket(limit, now)).tryAcquire(now);
        if (retryAfter > 0) {
            metrics.counter("gateway.ratelimit.rejected", "tier", tier).increment();
            response.setHeader("Retry-After", Long.toString(retryAfter));
            problemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
