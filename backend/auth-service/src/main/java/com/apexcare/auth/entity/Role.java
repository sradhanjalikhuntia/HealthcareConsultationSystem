package com.apexcare.auth.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * User roles in the ApexCare platform.
 *
 * <p><b>This enum must be identical across every service</b> — the JWT carries
 * the role as a string, and each service resolves it with
 * {@code Role.valueOf(...)}. A role issued here but missing downstream (or
 * vice versa) causes {@code IllegalArgumentException} → 401 → silent lockout.
 *
 * <p>Do not add roles here that no service actually uses. Do not remove roles
 * that any service references.
 */
public enum Role {

    ADMIN,
    DOCTOR,
    NURSE,
    RECEPTIONIST,
    PATIENT,
    BILLING_ADMIN,
    SYSTEM;

    /** Roles allowed to view or act on clinical data. */
    private static final Set<Role> CLINICAL = EnumSet.of(ADMIN, DOCTOR, NURSE);

    /** Roles allowed to manage appointments, admissions, referrals. */
    private static final Set<Role> STAFF = EnumSet.of(ADMIN, DOCTOR, NURSE, RECEPTIONIST);

    /** Roles considered non-human / service accounts. */
    private static final Set<Role> SERVICE_ACCOUNTS = EnumSet.of(SYSTEM);

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isDoctor() {
        return this == DOCTOR;
    }

    public boolean isNurse() {
        return this == NURSE;
    }

    public boolean isPatient() {
        return this == PATIENT;
    }

    public boolean isReceptionist() {
        return this == RECEPTIONIST;
    }

    public boolean isBillingAdmin() {
        return this == BILLING_ADMIN;
    }

    public boolean isSystem() {
        return this == SYSTEM;
    }

    /** True for ADMIN / DOCTOR / NURSE. */
    public boolean isClinical() {
        return CLINICAL.contains(this);
    }

    /** True for ADMIN / DOCTOR / NURSE / RECEPTIONIST. */
    public boolean isStaff() {
        return STAFF.contains(this);
    }

    /** True for SYSTEM (audit jobs, schedulers, etc.). */
    public boolean isServiceAccount() {
        return SERVICE_ACCOUNTS.contains(this);
    }

    /** Spring Security authority name (e.g. {@code ROLE_DOCTOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
