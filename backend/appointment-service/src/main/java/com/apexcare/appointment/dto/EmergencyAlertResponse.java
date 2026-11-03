package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.EmergencyAlert;
import com.apexcare.appointment.entity.EmergencyCodeType;
import com.apexcare.appointment.entity.EmergencySeverity;
import java.time.Instant;
import java.util.UUID;

public record EmergencyAlertResponse(
        UUID id,
        EmergencyCodeType codeType,
        String location,
        EmergencySeverity severity,
        UUID triggeredBy,
        String triggeredByName,
        boolean active,
        String notes,
        Instant triggeredAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolutionNotes) {

    public static EmergencyAlertResponse from(EmergencyAlert alert) {
        return new EmergencyAlertResponse(
                alert.getId(), alert.getCodeType(), alert.getLocation(), alert.getSeverity(),
                alert.getTriggeredBy(), alert.getTriggeredByName(), alert.isActive(),
                alert.getNotes(), alert.getTriggeredAt(), alert.getResolvedAt(),
                alert.getResolvedBy(), alert.getResolutionNotes());
    }
}
