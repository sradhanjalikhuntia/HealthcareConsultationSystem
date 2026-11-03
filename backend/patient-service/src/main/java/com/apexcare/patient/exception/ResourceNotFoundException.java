package com.apexcare.patient.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (patient, bed, referral, etc.) does not
 * exist or has been soft-deleted.
 *
 * <p>Maps to HTTP 404 Not Found.
 *
 * <p><b>Security:</b> For IDOR protection, also throw this (not
 * {@link AuthorizationException}) when the caller is not allowed to see a
 * resource — returning 404 instead of 403 avoids leaking that the resource
 * exists.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found: " + id);
    }

    // ---- Factory methods (preferred usage) ----

    public static ResourceNotFoundException of(String resourceType, Object id) {
        return new ResourceNotFoundException(resourceType + " not found: " + id);
    }
}
