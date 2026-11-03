package com.apexcare.appointment.exception;

public class InvalidCredentialsException extends RuntimeException {
    private InvalidCredentialsException(String message) {
        super(message);
    }

    public static InvalidCredentialsException missingToken() {
        return new InvalidCredentialsException("Authentication token is required");
    }

    public static InvalidCredentialsException expiredToken() {
        return new InvalidCredentialsException("Authentication token has expired");
    }

    public static InvalidCredentialsException invalidToken() {
        return new InvalidCredentialsException("Authentication token is invalid");
    }
}
