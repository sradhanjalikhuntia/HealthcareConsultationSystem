package com.apexcare.billing.entity;

public enum Role {
    ADMIN,
    DOCTOR,
    NURSE,
    RECEPTIONIST,
    PATIENT,
    BILLING_ADMIN,
    PHARMACIST,        // ← required for PharmacyService
    LAB_TECHNICIAN,
    SYSTEM
}
