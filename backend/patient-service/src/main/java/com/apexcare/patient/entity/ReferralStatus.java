package com.apexcare.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReferralStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    COMPLETED,
    CANCELLED;

    @JsonCreator
    public static ReferralStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String clean = value.trim().toUpperCase();
        for (ReferralStatus s : values()) {
            if (s.name().equalsIgnoreCase(clean)) {
                return s;
            }
        }
        return PENDING;
    }
}
