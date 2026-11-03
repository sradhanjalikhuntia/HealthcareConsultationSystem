package com.apexcare.patient.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when authentication fails: invalid username/password, expired or
 * malformed JWT, or any case where the caller's identity could not be verified.
 *
 * <p>Maps to HTTP 401 Unauthorized.
 * <p>For authorization failures (valid user, insufficient permission), use
 * {@link org.springframework.security.access.AccessDeniedException} instead.
 *
 * <p><b>Security:</b> Always use the factory methods below. Never expose
 * whether the username exists or the password was wrong — that enables
 * username enumeration attacks.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }

    // ---- Factory methods (preferred usage) ----

    /** Generic bad-login message — same for unknown user and wrong password. */
    public static InvalidCredentialsException badCredentials() {
        return new InvalidCredentialsException("Invalid username or password");
    }

    public static InvalidCredentialsException expiredToken() {
        return new InvalidCredentialsException("Authentication token expired");
    }

    public static InvalidCredentialsException invalidToken() {
        return new InvalidCredentialsException("Invalid authentication token");
    }

    public static InvalidCredentialsException missingToken() {
        return new InvalidCredentialsException("Authentication token is missing");
    }
}
