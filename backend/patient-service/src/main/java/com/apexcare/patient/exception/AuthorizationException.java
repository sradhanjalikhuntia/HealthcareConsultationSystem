package com.apexcare.patient.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated user lacks permission to perform the requested
 * action — wrong role, wrong ownership, or any policy violation.
 *
 * <p>Maps to HTTP 403 Forbidden.
 *
 * <p>For authentication failures (no valid identity), use
 * {@link InvalidCredentialsException} instead.
 *
 * <p><b>Security:</b> When hiding resource existence (IDOR protection),
 * prefer {@link ResourceNotFoundException} over this class — returning 404
 * instead of 403 avoids leaking that the resource exists.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AuthorizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }

    // ---- Factory methods (preferred usage) ----

    public static AuthorizationException roleRequired(String role) {
        return new AuthorizationException(role + " role required");
    }

    public static AuthorizationException notOwner() {
        return new AuthorizationException("Not authorized to access this resource");
    }

    public static AuthorizationException insufficientPrivileges() {
        return new AuthorizationException("Insufficient privileges");
    }
}
