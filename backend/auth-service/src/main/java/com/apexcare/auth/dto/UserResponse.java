package com.apexcare.auth.dto;

import com.apexcare.auth.entity.Role;
import com.apexcare.auth.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe, read-only representation of a {@link User}.
 *
 * <p>Never exposes the password hash. Timestamps are ISO-8601.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private UUID linkedPatientId;
    private String doctorCode;
    private boolean enabled;
    private boolean emailVerified;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastLoginAt;

    public static UserResponse from(User user) {
        if (user == null) return null;

        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.username = user.getUsername();
        response.email = user.getEmail();
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.role = user.getRole();
        response.linkedPatientId = user.getLinkedPatientId();
        response.doctorCode = user.getDoctorCode();
        response.enabled = user.isEnabled();
        response.emailVerified = user.isEmailVerified();
        response.createdAt = user.getCreatedAt();
        response.updatedAt = user.getUpdatedAt();
        response.lastLoginAt = user.getLastLoginAt();
        return response;
    }

    // ---- getters ----

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Role getRole() { return role; }
    public UUID getLinkedPatientId() { return linkedPatientId; }
    public String getDoctorCode() { return doctorCode; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    /** Convenience — full display name. Not serialized as a separate field. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getFullName() {
        String first = firstName == null ? "" : firstName;
        String last  = lastName  == null ? "" : lastName;
        String name = (first + " " + last).trim();
        return name.isBlank() ? username : name;
    }
}
