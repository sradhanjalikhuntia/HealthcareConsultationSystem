package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.EmergencySeverity;

public record AmbulanceTriageRequest(
        String patientName,
        Integer patientAge,
        String chiefComplaint,
        String location,
        EmergencySeverity severity) {
}
