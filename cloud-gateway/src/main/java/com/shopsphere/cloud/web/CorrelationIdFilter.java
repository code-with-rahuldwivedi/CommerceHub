package com.shopsphere.cloud.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * First filter in the chain. Gives every request one well-formed X-Request-Id (client value only if it is safe,
 * otherwise a new UUID), puts it in the log context, echoes it in the response and passes it downstream.
 * It also removes headers that only internal callers may send, so a client can never reach a downstream
 * service pretending to be another service.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_\\-]{8,64}$");
    private static final List<String> INTERNAL_ONLY_HEADERS = List.of("X-Service-Token");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }

        HeaderRequestWrapper wrapped = new HeaderRequestWrapper(request);
        wrapped.set(HEADER, id);
        INTERNAL_ONLY_HEADERS.forEach(wrapped::remove);

        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
