package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.EmergencyCodeType;
import com.apexcare.appointment.entity.EmergencySeverity;

public record EmergencyAlertRequest(
        EmergencyCodeType codeType,
        String location,
        EmergencySeverity severity,
        String notes) {
}
