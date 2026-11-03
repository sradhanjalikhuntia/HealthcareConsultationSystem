package com.apexcare.appointment.security;

import com.apexcare.appointment.entity.Role;

import java.util.UUID;

/**
 * Authenticated principal extracted from a validated JWT.
 *
 * <p>Populated by {@link JwtAuthenticationFilter} on each request and consumed
 * by controllers/services via {@code @AuthenticationPrincipal}.
 *
 * <p>The {@code role} is stored as a {@link Role} enum, never a raw string,
 * so role checks are compile-time safe.
 */
public record JwtPrincipal(
        UUID userId,
        String username,
        Role role,
        UUID linkedPatientId,
        String doctorCode
) {

    public JwtPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required in JwtPrincipal");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required in JwtPrincipal");
        }
    }

    // ---- Role helpers ----

    public boolean hasRole(Role required) {
        return this.role == required;
    }

    public boolean hasAnyRole(Role... required) {
        if (required == null) return false;
        for (Role r : required) {
            if (this.role == r) return true;
        }
        return false;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isDoctor() {
        return this.role == Role.DOCTOR;
    }

    public boolean isNurse() {
        return this.role == Role.NURSE;
    }

    public boolean isPatient() {
        return this.role == Role.PATIENT;
    }

    public boolean isReceptionist() {
        return this.role == Role.RECEPTIONIST;
    }

    public boolean isBillingAdmin() {
        return this.role == Role.BILLING_ADMIN;
    }

    // ---- Ownership helpers ----

    /**
     * Whether this principal is linked to the given patient record.
     * Used for IDOR checks (a patient may only access their own record).
     */
    public boolean isLinkedToPatient(UUID patientId) {
        return patientId != null && patientId.equals(this.linkedPatientId);
    }

    /** Authority name in Spring Security format (e.g. {@code ROLE_DOCTOR}). */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
