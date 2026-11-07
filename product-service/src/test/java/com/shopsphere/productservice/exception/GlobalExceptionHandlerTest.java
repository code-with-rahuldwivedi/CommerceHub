
package com.shopsphere.productservice.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // 1. Invalid request - 400
    @Test
    void handleInvalidRequestShouldReturnBadRequest() {

        InvalidRequestException ex =
                new InvalidRequestException("Invalid product request");

        ProblemDetail result = handler.handleInvalidRequest(ex);

        assertEquals(400, result.getStatus());
        assertEquals("Invalid product request", result.getDetail());
    }

    // 2. Product not found - 404
    @Test
    void handleNotFoundShouldReturnNotFound() {

        ProductNotFoundException ex =
                new ProductNotFoundException();

        ProblemDetail result = handler.handleNotFound(ex);

        assertEquals(404, result.getStatus());
        assertEquals("Product not found", result.getDetail());
    }

    // 3. Authentication failure - 401
    @Test
    void handleAuthenticationShouldReturnUnauthorized() {

        BadCredentialsException ex =
                new BadCredentialsException("Invalid credentials");

        ProblemDetail result = handler.handleAuthentication(ex);

        assertEquals(401, result.getStatus());
        assertEquals("Authentication failed", result.getDetail());
    }

    // 4. Access denied - 403
    @Test
    void handleAccessDeniedShouldReturnForbidden() {

        AccessDeniedException ex =
                new AccessDeniedException("Not allowed");

        ProblemDetail result = handler.handleAccessDenied(ex);

        assertEquals(403, result.getStatus());
        assertEquals("Access denied", result.getDetail());
    }

    // 5. Conflict - 409
    @Test
    void handleConflictShouldReturnConflict() {

        ConflictException ex =
                new ConflictException("Product already exists");

        ProblemDetail result = handler.handleConflict(ex);

        assertEquals(409, result.getStatus());
        assertEquals("Product already exists", result.getDetail());
    }

    // 6. Concurrent update failure - 409
    @Test
    void handleConcurrencyShouldReturnConflict() {

        ConcurrencyFailureException ex =
                new ConcurrencyFailureException("Concurrent update");

        ProblemDetail result = handler.handleConcurrency(ex);

        assertEquals(409, result.getStatus());
        assertEquals(
                "The product was modified concurrently, reload it and retry",
                result.getDetail()
        );
    }

    // 7. Data integrity violation - 409
    @Test
    void handleIntegrityShouldReturnConflict() {

        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("Duplicate record");

        ProblemDetail result = handler.handleIntegrity(ex);

        assertEquals(409, result.getStatus());
        assertEquals(
                "Request conflicts with existing data",
                result.getDetail()
        );
    }

    // 8. Unexpected exception - 500
    @Test
    void handleUnexpectedShouldReturnInternalServerError() {

        Exception ex = new RuntimeException("Unexpected failure");

        ProblemDetail result = handler.handleUnexpected(ex);

        assertEquals(500, result.getStatus());
        assertEquals("Unexpected error", result.getDetail());
    }

    // 9. Validation errors - 400
    @Test
    void handleMethodArgumentNotValidShouldReturnValidationErrors()
            throws Exception {

        TestRequest request = new TestRequest();

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(request, "request");

        bindingResult.rejectValue(
                "name", "NotBlank", "Name is required"
        );

        bindingResult.rejectValue(
                "price", "Positive", "Price must be positive"
        );

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class
                        .getDeclaredMethod("sampleMethod", String.class),
                0
        );

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(
                        parameter, bindingResult
                );

        ResponseEntity<Object> response =
                handler.handleMethodArgumentNotValid(
                        ex,
                        new HttpHeaders(),
                        HttpStatus.BAD_REQUEST,
                        null
                );

        assertEquals(400, response.getStatusCode().value());

        assertInstanceOf(ProblemDetail.class, response.getBody());

        ProblemDetail body = (ProblemDetail) response.getBody();

        assertEquals(400, body.getStatus());
        assertEquals("Validation failed", body.getDetail());

        @SuppressWarnings("unchecked")
        Map<String, String> errors =
                (Map<String, String>) body.getProperties().get("errors");

        assertNotNull(errors);
        assertEquals(2, errors.size());
        assertEquals("Name is required", errors.get("name"));
        assertEquals("Price must be positive", errors.get("price"));
    }

    private void sampleMethod(String name) {
        // Used only for MethodParameter creation.
    }

    // Test bean for validation
    static class TestRequest {

        private String name;
        private Double price;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }
    }
}
