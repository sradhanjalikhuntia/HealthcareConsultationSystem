package com.apexcare.patient.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error body returned by {@code GlobalExceptionHandler}.
 *
 * <p>Example:
 * <pre>
 * {
 *   "success": false,
 *   "timestamp": "2026-09-20T12:34:56.789Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "email: must be a well-formed email address",
 *   "path": "/api/patients",
 *   "requestId": "3f9a8c1e-...",
 *   "details": { "email": "must be a well-formed email address" }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success = false;
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final String requestId;
    private final Map<String, String> details;

    public ErrorResponse(Instant timestamp,
                         int status,
                         String error,
                         String message,
                         String path,
                         String requestId,
                         Map<String, String> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
        this.details = details;
    }

    /** Convenience constructor without requestId/details. */
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null, null);
    }

    public ErrorResponse(Instant timestamp, int status, String error, String message,
                         String path, String requestId) {
        this(timestamp, status, error, message, path, requestId, null);
    }

    public boolean isSuccess() { return success; }
    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getRequestId() { return requestId; }
    public Map<String, String> getDetails() { return details; }
}
