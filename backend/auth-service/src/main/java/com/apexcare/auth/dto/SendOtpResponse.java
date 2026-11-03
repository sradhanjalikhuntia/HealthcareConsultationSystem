package com.apexcare.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendOtpResponse {

    private String email;
    private String message;
    private int expiresInSeconds;

    public SendOtpResponse() {}

    public SendOtpResponse(String email, String message, int expiresInSeconds) {
        this.email = email;
        this.message = message;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
