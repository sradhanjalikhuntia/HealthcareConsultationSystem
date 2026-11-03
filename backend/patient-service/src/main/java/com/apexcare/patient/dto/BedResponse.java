package com.apexcare.patient.dto;

import com.apexcare.patient.entity.Bed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BedResponse(
        UUID id,
        String bedNumber,
        String wardType,
        String status,
        BigDecimal dailyRate,
        UUID currentPatientId,
        String currentPatientName,
        Instant lastOccupiedAt,
        Instant lastDischargedAt,
        Instant lastSanitizedAt,
        Integer stayDays,
        BigDecimal totalCharge,
        UUID billId
) {
    public static BedResponse from(Bed b) {
        return from(b, null, null, null);
    }

    public static BedResponse from(Bed b, Integer stayDays, BigDecimal totalCharge, UUID billId) {
        return new BedResponse(
                b.getId(),
                b.getBedNumber(),
                b.getWardType() == null ? null : b.getWardType().name(),
                b.getStatus() == null ? null : b.getStatus().name(),
                b.getDailyRate(),
                b.getCurrentPatientId(),
                b.getCurrentPatientName(),
                b.getLastOccupiedAt(),
                b.getLastDischargedAt(),
                b.getLastSanitizedAt(),
                stayDays,
                totalCharge,
                billId
        );
    }
}
