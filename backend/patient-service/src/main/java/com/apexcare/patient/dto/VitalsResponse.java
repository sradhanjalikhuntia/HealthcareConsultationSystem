package com.apexcare.patient.dto;

import com.apexcare.patient.entity.PatientVitals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalsResponse(
        UUID id,
        UUID patientId,
        Instant recordedAt,
        UUID recordedBy,
        Integer heartRate,
        Integer systolicBp,
        Integer diastolicBp,
        String bloodPressure,
        Integer spO2,
        Integer oxygenSaturation,
        Integer respiratoryRate,
        BigDecimal temperature,
        BigDecimal weight,
        String spo2Scale,
        String consciousness,
        boolean onSupplementalOxygen,
        Integer news2Score,
        String riskLevel,
        String clinicalAlert,
        boolean singleParamCritical
) {
    public static VitalsResponse from(PatientVitals v) {
        String bp = null;
        if (v.getSystolicBp() != null && v.getDiastolicBp() != null) {
            bp = v.getSystolicBp() + "/" + v.getDiastolicBp();
        } else if (v.getSystolicBp() != null) {
            bp = v.getSystolicBp() + "/-";
        }
        Integer spo2 = v.getSpO2();

        return new VitalsResponse(
                v.getId(),
                v.getPatientId(),
                v.getRecordedAt(),
                v.getRecordedBy(),
                v.getHeartRate(),
                v.getSystolicBp(),
                v.getDiastolicBp(),
                bp,
                spo2,
                spo2,
                v.getRespiratoryRate(),
                v.getTemperature(),
                v.getWeight(),
                v.getSpo2Scale() == null ? null : v.getSpo2Scale().name(),
                v.getConsciousness() == null ? null : v.getConsciousness().name(),
                v.isOnSupplementalOxygen(),
                v.getNews2Score(),
                v.getRiskLevel() == null ? null : v.getRiskLevel().name(),
                v.getClinicalAlert(),
                v.isSingleParamCritical()
        );
    }
}
