package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.AmbulanceStatus;
import com.apexcare.appointment.entity.AmbulanceTriage;
import com.apexcare.appointment.entity.EmergencySeverity;
import java.time.Instant;
import java.util.UUID;

public record AmbulanceTriageResponse(
        UUID id,
        String patientName,
        Integer patientAge,
        String chiefComplaint,
        String location,
        EmergencySeverity severity,
        AmbulanceStatus status,
        UUID registeredBy,
        Instant dispatchedAt,
        Instant updatedAt) {

    public static AmbulanceTriageResponse from(AmbulanceTriage value) {
        return new AmbulanceTriageResponse(
                value.getId(), value.getPatientName(), value.getPatientAge(),
                value.getChiefComplaint(), value.getLocation(), value.getSeverity(),
                value.getStatus(), value.getRegisteredBy(), value.getDispatchedAt(),
                value.getUpdatedAt());
    }
}
