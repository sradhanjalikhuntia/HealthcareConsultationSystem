package com.apexcare.auth.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard error body returned by {@code GlobalExceptionHandler},
 * {@code JwtAuthenticationFilter}, and {@code SecurityConfig} handlers.
 *
 * <p>Kept consistent across all ApexCare services so frontend parsers are
 * uniform.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success = false;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant timestamp;

    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final String requestId;

    public ErrorResponse(Instant timestamp,
                         int status,
                         String error,
                         String message,
                         String path,
                         String requestId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
    }

    /** Convenience — requestId omitted. */
    public ErrorResponse(Instant timestamp, int status, String error,
                         String message, String path) {
        this(timestamp, status, error, message, path, null);
    }

    public boolean isSuccess() { return success; }
    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getRequestId() { return requestId; }
}
