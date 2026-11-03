package com.apexcare.billing.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Convenience factory — produces a consistent "X not found: id" message.
     *
     * @param resource human-readable resource type, e.g. "Bill", "Patient"
     * @param id       the resource identifier (UUID, Long, String, etc.)
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " not found: " + id);
    }
}
