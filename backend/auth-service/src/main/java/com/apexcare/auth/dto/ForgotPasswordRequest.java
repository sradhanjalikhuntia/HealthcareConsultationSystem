package com.apexcare.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    public String getEmail() { return email; }

    public void setEmail(String email) {
        // Normalize so lookups are case-insensitive and consistent with
        // how emails are stored.
        this.email = email == null ? null : email.trim().toLowerCase();
    }
}
