package com.shopsphere.cloud.web;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects oversized bodies announced by Content-Length (413) and, optionally, bodies sent without a length
 * (chunked, 411): the downstream services only check Content-Length, so this is where chunked uploads are stopped.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

    private final long maxBytes;
    private final boolean rejectChunked;
    private final ProblemWriter problemWriter;

    public RequestSizeLimitFilter(long maxBytes, boolean rejectChunked, ProblemWriter problemWriter) {
        this.maxBytes = maxBytes;
        this.rejectChunked = rejectChunked;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METHODS_WITH_BODY.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            problemWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large");
            return;
        }
        String transferEncoding = request.getHeader("Transfer-Encoding");
        if (rejectChunked && request.getContentLengthLong() < 0 && transferEncoding != null
                && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            problemWriter.write(response, HttpStatus.LENGTH_REQUIRED, "Content-Length is required");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
