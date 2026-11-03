package com.apexcare.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReferralPriority {
    ROUTINE,
    URGENT,
    EMERGENCY;

    @JsonCreator
    public static ReferralPriority fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ROUTINE;
        }
        String clean = value.trim().toUpperCase();
        for (ReferralPriority p : values()) {
            if (p.name().equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return ROUTINE;
    }
}
