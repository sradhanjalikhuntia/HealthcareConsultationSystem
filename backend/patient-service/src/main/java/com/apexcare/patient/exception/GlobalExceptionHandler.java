package com.apexcare.patient.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MDC_REQUEST_ID = "requestId";

    // -----------------------------------------------------------------
    // 404 — Resource not found
    // -----------------------------------------------------------------
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("404 {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    // -----------------------------------------------------------------
    // 400 — Bad request
    // -----------------------------------------------------------------
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest req) {
        log.warn("400 {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        (@NonNull FieldError fe) -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a + "; " + b,
                        TreeMap::new));

        String summary = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));

        log.warn("400 (validation) {} {} — {}", req.getMethod(), req.getRequestURI(), summary);
        return buildWithDetails(HttpStatus.BAD_REQUEST, summary, fieldErrors, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage() != null ? v.getMessage() : "invalid",
                        (a, b) -> a + "; " + b,
                        TreeMap::new));

        String summary = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));

        log.warn("400 (constraint) {} {} — {}", req.getMethod(), req.getRequestURI(), summary);
        return buildWithDetails(HttpStatus.BAD_REQUEST, summary, fieldErrors, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("400 (malformed JSON) {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.warn("400 (type mismatch) {} {} — {}", req.getMethod(), req.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    // -----------------------------------------------------------------
    // 401 — Unauthenticated
    // -----------------------------------------------------------------
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(InvalidCredentialsException ex, HttpServletRequest req) {
        log.warn("401 {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        // Generic message — do not leak whether username/email exists
        return build(HttpStatus.UNAUTHORIZED, "Authentication required or credentials invalid", req);
    }

    // -----------------------------------------------------------------
    // 403 — Forbidden
    // -----------------------------------------------------------------
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorization(AuthorizationException ex, HttpServletRequest req) {
        log.warn("403 {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Insufficient privileges", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("403 (Spring) {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Insufficient privileges", req);
    }

    // -----------------------------------------------------------------
    // 405 — Method not allowed
    // -----------------------------------------------------------------
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        log.warn("405 {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    // -----------------------------------------------------------------
    // 409 — Conflict (concurrency / duplicates)
    // -----------------------------------------------------------------
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("409 (optimistic lock) {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT,
                "Resource was modified by another request. Please retry.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        // Log the underlying cause for debugging, but don't leak to client
        log.warn("409 (data integrity) {} {} — {}",
                req.getMethod(), req.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Resource already exists or violates a constraint", req);
    }

    // -----------------------------------------------------------------
    // 500 — Catch-all
    // -----------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("500 {} {} — {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private ResponseEntity<ErrorResponse> build(@NonNull HttpStatus status,
                                                String message,
                                                HttpServletRequest req) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                MDC.get(MDC_REQUEST_ID)
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ErrorResponse> buildWithDetails(@NonNull HttpStatus status,
                                                           String message,
                                                           Map<String, String> details,
                                                           HttpServletRequest req) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                MDC.get(MDC_REQUEST_ID),
                details
        );
        return ResponseEntity.status(status).body(body);
    }
}
