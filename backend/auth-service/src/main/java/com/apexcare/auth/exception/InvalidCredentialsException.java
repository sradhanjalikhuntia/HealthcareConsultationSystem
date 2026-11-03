package com.apexcare.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }

    // ---- Factory methods ----

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
