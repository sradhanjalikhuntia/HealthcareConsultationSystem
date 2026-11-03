package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.EmergencyCodeType;
import com.apexcare.appointment.entity.EmergencySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for broadcasting an emergency code.
 *
 * <p>Note: {@code triggeredBy} is intentionally NOT part of this DTO — the
 * caller's identity is taken from the authenticated JWT principal by the
 * service layer. Clients cannot impersonate another user.
 */
public class EmergencyBroadcastRequest {

    @NotNull
    private EmergencyCodeType codeType;

    @NotBlank
    @Size(max = 100)
    private String location;

    @NotNull
    private EmergencySeverity severity;

    @Size(max = 1000)
    private String notes;

    public EmergencyBroadcastRequest() {}

    public EmergencyBroadcastRequest(EmergencyCodeType codeType,
                                     String location,
                                     EmergencySeverity severity,
                                     String notes) {
        this.codeType = codeType;
        this.location = location;
        this.severity = severity;
        this.notes = notes;
    }

    public EmergencyCodeType getCodeType() { return codeType; }
    public void setCodeType(EmergencyCodeType codeType) { this.codeType = codeType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public EmergencySeverity getSeverity() { return severity; }
    public void setSeverity(EmergencySeverity severity) { this.severity = severity; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
