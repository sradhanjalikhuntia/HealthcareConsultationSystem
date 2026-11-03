package com.apexcare.appointment.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }

    public ResourceNotFoundException(String resource, Object id) {
        this(resource + " not found: " + id);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource, id);
    }
}
