package com.shopsphere.userservice.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.shopsphere.userservice.entity.User;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleMethodArgumentNotValid_returnsBadRequestWithFieldErrors() {

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new User(), "user");

        bindingResult.addError(
                new FieldError("user", "email", "Invalid email")
        );

        bindingResult.addError(
                new FieldError("user", "name", "Name is required")
        );

        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Object> response =
                handler.handleMethodArgumentNotValid(
                        exception,
                        null,
                        HttpStatus.BAD_REQUEST,
                        null
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ProblemDetail problem = (ProblemDetail) response.getBody();

        assertNotNull(problem);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Validation failed", problem.getDetail());

        @SuppressWarnings("unchecked")
        Map<String, String> errors =
                (Map<String, String>) problem.getProperties().get("errors");

        assertEquals(2, errors.size());
        assertEquals("Invalid email", errors.get("email"));
        assertEquals("Name is required", errors.get("name"));
    }

    @Test
    void handleBadRequest_returns400() {

        InvalidRequestException exception =
                mock(InvalidRequestException.class);

        when(exception.getMessage()).thenReturn("Invalid request");

        ProblemDetail problem = handler.handleBadRequest(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Invalid request", problem.getDetail());
    }

    @Test
    void handleUnauthorized_returns401() {

        InvalidCredentialsException exception =
                mock(InvalidCredentialsException.class);

        when(exception.getMessage()).thenReturn("Invalid credentials");

        ProblemDetail problem = handler.handleUnauthorized(exception);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Invalid credentials", problem.getDetail());
    }

    @Test
    void handleAuthentication_returns401() {

        AuthenticationException exception =
                mock(AuthenticationException.class);

        ProblemDetail problem = handler.handleAuthentication(exception);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Authentication failed", problem.getDetail());
    }

    @Test
    void handleNotVerified_returns403() {

        EmailNotVerifiedException exception =
                mock(EmailNotVerifiedException.class);

        when(exception.getMessage()).thenReturn("Email not verified");

        ProblemDetail problem = handler.handleNotVerified(exception);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Email not verified", problem.getDetail());
    }

    @Test
    void handleAccessDenied_returns403() {

        AccessDeniedException exception =
                mock(AccessDeniedException.class);

        ProblemDetail problem = handler.handleAccessDenied(exception);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Access denied", problem.getDetail());
    }

    @Test
    void handleNotFound_returns404() {

        UserNotFoundException exception =
                mock(UserNotFoundException.class);

        when(exception.getMessage()).thenReturn("User not found");

        ProblemDetail problem = handler.handleNotFound(exception);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("User not found", problem.getDetail());
    }

    @Test
    void handleConflict_returns409() {

        EmailAlreadyExistsException exception =
                mock(EmailAlreadyExistsException.class);

        when(exception.getMessage()).thenReturn("Email already exists");

        ProblemDetail problem = handler.handleConflict(exception);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Email already exists", problem.getDetail());
    }

    @Test
    void handleIntegrity_returns409() {

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("Duplicate entry");

        ProblemDetail problem = handler.handleIntegrity(exception);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals(
                "Request conflicts with existing data",
                problem.getDetail()
        );
    }

    @Test
    void handleConcurrency_returns409() {

        ConcurrencyFailureException exception =
                new ConcurrencyFailureException("Concurrent update");

        ProblemDetail problem = handler.handleConcurrency(exception);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals(
                "Concurrent request conflict, please retry",
                problem.getDetail()
        );
    }

    @Test
    void handleUnexpected_returns500() {

        Exception exception = new RuntimeException("Unexpected database error");

        ProblemDetail problem = handler.handleUnexpected(exception);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                problem.getStatus()
        );

        assertEquals("Unexpected error", problem.getDetail());
    }
}
