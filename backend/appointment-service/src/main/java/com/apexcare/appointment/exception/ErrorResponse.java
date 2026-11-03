package com.apexcare.appointment.exception;

import java.time.Instant;

public class ErrorResponse {
    private boolean success = false;
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String requestId;
    private Object data;
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp; this.status = status; this.error = error; this.message = message; this.path = path;
    }
    public ErrorResponse(Instant timestamp, int status, String error, String message,
                         String path, String requestId) {
        this(timestamp, status, error, message, path);
        this.requestId = requestId;
    }
    public ErrorResponse(Instant timestamp, int status, String error, String message,
                         String path, String requestId, Object data) {
        this(timestamp, status, error, message, path, requestId);
        this.data = data;
    }
    public boolean isSuccess() { return success; }
    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getRequestId() { return requestId; }
    public Object getData() { return data; }
}
