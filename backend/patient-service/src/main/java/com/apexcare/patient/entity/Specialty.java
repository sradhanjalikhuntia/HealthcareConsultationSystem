package com.apexcare.patient.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Specialty {
    GENERAL_MEDICINE,
    CARDIOLOGY,
    NEUROLOGY,
    PEDIATRICS,
    SURGERY,
    ONCOLOGY,
    OTHER;

    @JsonCreator
    public static Specialty fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String clean = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        for (Specialty s : values()) {
            if (s.name().equalsIgnoreCase(clean)) {
                return s;
            }
        }
        if (clean.contains("CARDIO")) return CARDIOLOGY;
        if (clean.contains("NEURO")) return NEUROLOGY;
        if (clean.contains("PEDIA")) return PEDIATRICS;
        if (clean.contains("SURG")) return SURGERY;
        if (clean.contains("ONCO") || clean.contains("CANCER")) return ONCOLOGY;
        if (clean.contains("MED") || clean.contains("GEN")) return GENERAL_MEDICINE;
        return OTHER;
    }
}
