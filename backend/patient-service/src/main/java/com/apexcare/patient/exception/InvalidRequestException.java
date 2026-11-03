package com.apexcare.patient.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the client sends a request that is syntactically valid but
 * semantically wrong — e.g. cross-field violations, invalid state transitions,
 * or business rule failures that are not already covered by bean validation.
 *
 * <p>Maps to HTTP 400 Bad Request.
 * <p>For bean-validation failures ({@code @Valid} on request bodies), the
 * {@code GlobalExceptionHandler} handles {@code MethodArgumentNotValidException}
 * separately.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    // ---- Factory methods (preferred usage) ----

    public static InvalidRequestException of(String message) {
        return new InvalidRequestException(message);
    }

    public static InvalidRequestException field(String field, String reason) {
        return new InvalidRequestException(field + ": " + reason);
    }
}
