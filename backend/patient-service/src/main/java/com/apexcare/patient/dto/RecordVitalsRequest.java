package com.apexcare.patient.dto;

import com.apexcare.patient.entity.ConsciousnessLevel;
import com.apexcare.patient.entity.Spo2Scale;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordVitalsRequest(
        UUID patientId,
        Integer heartRate,
        Integer systolicBp,
        Integer diastolicBp,
        String bloodPressure,
        Integer spO2,
        Integer oxygenSaturation,
        Integer respiratoryRate,
        BigDecimal temperature,
        BigDecimal weight,
        Spo2Scale spo2Scale,
        ConsciousnessLevel consciousness,
        Boolean onSupplementalOxygen
) {
    public Integer effectiveSystolicBp() {
        if (systolicBp != null) return systolicBp;
        if (bloodPressure != null && bloodPressure.contains("/")) {
            try {
                String[] parts = bloodPressure.split("/");
                return Integer.parseInt(parts[0].trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    public Integer effectiveDiastolicBp() {
        if (diastolicBp != null) return diastolicBp;
        if (bloodPressure != null && bloodPressure.contains("/")) {
            try {
                String[] parts = bloodPressure.split("/");
                return Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    public Integer effectiveSpO2() {
        if (spO2 != null) return spO2;
        if (oxygenSaturation != null) return oxygenSaturation;
        return null;
    }
}
