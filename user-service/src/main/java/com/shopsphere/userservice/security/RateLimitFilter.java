package com.shopsphere.userservice.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shopsphere.userservice.config.AppProperties;
import com.shopsphere.userservice.service.AuthMetrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP, per-endpoint fixed-window limiter for the unauthenticated auth endpoints. It is per
 * instance (in memory): ALSO enforce a limit at the API gateway for a global, cross-replica limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify-email",
            "/api/auth/resend-verification");

    private final AppProperties.RateLimit props;
    private final ProblemDetailWriter problemWriter;
    private final AuthMetrics metrics;
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1)) // entry created at first hit => fixed 1-minute window
            .maximumSize(100_000)                    // bounded memory under IP-spray attacks
            .build();

    public RateLimitFilter(AppProperties.RateLimit props, ProblemDetailWriter problemWriter, AuthMetrics metrics) {
        this.props = props;
        this.problemWriter = problemWriter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.enabled()
                || !"POST".equals(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String key = request.getRemoteAddr() + "|" + path;
        int count = counters.get(key, k -> new AtomicInteger()).incrementAndGet();

        if (count > props.authRequestsPerMinute()) {
            metrics.rateLimited(path.substring(path.lastIndexOf('/') + 1));
            response.setHeader("Retry-After", "60");
            problemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
