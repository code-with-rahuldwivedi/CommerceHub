package com.shopsphere.cloud.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Target of the circuit-breaker FORWARD (never reachable by clients: see SecurityConfig). It answers ANY HTTP method
 * because the forward keeps the original one. For requests that change data the answer says that the outcome is
 * unknown and how to retry safely.
 */
@RestController
public class FallbackController {

    private static final Map<String, String> SERVICES = Map.of(
            "user-service", "Account service",
            "product-service", "Catalog service",
            "order-service", "Order service",
            "inventory-service", "Inventory service");

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ProblemDetail> fallback(@PathVariable String service, HttpServletRequest request) {
        String name = SERVICES.getOrDefault(service, "The requested service");

        String detail = SAFE_METHODS.contains(request.getMethod())
                ? name + " is temporarily unavailable. Please try again shortly."
                : name + " did not answer in time, so the result of this request is unknown. "
                  + "Retry with the SAME Idempotency-Key (orders) or check the resource before repeating it.";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setProperty("retryable", true);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
