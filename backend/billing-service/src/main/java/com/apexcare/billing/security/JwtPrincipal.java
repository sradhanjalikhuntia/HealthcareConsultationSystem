package com.apexcare.billing.security;

import com.apexcare.billing.entity.Role;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable principal extracted from a verified JWT token.
 * Passed as {@code @AuthenticationPrincipal} into controller methods.
 */
public record JwtPrincipal(UUID userId, String username, String role, UUID linkedPatientId, String doctorCode) {

    // ------------------------------------------------------------------
    // Role helpers
    // ------------------------------------------------------------------

    /** Returns {@code true} if this principal has the {@code PATIENT} role. */
    public boolean isPatient() {
        return Role.PATIENT.name().equals(role);
    }

    /** Returns {@code true} if this principal has exactly the given role. */
    public boolean hasRole(Role r) {
        return r != null && r.name().equals(role);
    }

    /** Returns {@code true} if this principal has any of the supplied roles. */
    public boolean hasAnyRole(Role... roles) {
        if (roles == null || role == null) return false;
        for (Role r : roles) {
            if (r != null && r.name().equals(role)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Patient-linking helper
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} when this principal's linked patient ID equals
     * the supplied {@code patientId}.  Always returns {@code false} for
     * non-patient principals (i.e. when {@link #linkedPatientId()} is null).
     */
    public boolean isLinkedToPatient(UUID patientId) {
        return linkedPatientId != null && Objects.equals(linkedPatientId, patientId);
    }
}
