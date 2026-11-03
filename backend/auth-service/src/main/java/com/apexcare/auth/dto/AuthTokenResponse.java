package com.apexcare.auth.dto;

import com.apexcare.auth.entity.Role;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned by the login / register endpoints.
 *
 * <p>Contains the JWT and a snapshot of the authenticated user's basic
 * profile. Sensitive fields (password hash, internal flags) are deliberately
 * absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthTokenResponse {

    private String token;

    /** Always "Bearer" — clients must send {@code Authorization: Bearer <token>}. */
    private final String tokenType = "Bearer";

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    private UUID userId;
    private String username;
    private Role role;
    private UUID linkedPatientId;
    private String doctorCode;
    private String firstName;
    private String lastName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastLoginAt;

    // ---- getters ----

    @JsonProperty("token")
    public String getToken() { return token; }

    @JsonProperty("accessToken")
    public String getAccessToken() { return token; }
    public String getTokenType() { return tokenType; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public UUID getLinkedPatientId() { return linkedPatientId; }
    public String getDoctorCode() { return doctorCode; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    // ---- setters (for Jackson / builder use) ----

    public void setToken(String token) { this.token = token; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(Role role) { this.role = role; }
    public void setLinkedPatientId(UUID linkedPatientId) { this.linkedPatientId = linkedPatientId; }
    public void setDoctorCode(String doctorCode) { this.doctorCode = doctorCode; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
