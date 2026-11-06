package com.shopsphere.userservice.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects oversized request bodies announced via Content-Length before any JSON parsing or BCrypt work.
 * (Chunked bodies without Content-Length are not covered: also set a body limit at the gateway.)
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ProblemDetailWriter problemWriter;

    public RequestSizeLimitFilter(long maxBytes, ProblemDetailWriter problemWriter) {
        this.maxBytes = maxBytes;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            problemWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
