package com.apexcare.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WardDischargeChargeRequest(
        UUID admissionId,
        UUID patientId,
        String patientName,
        String bedNumber,
        String wardType,
        Instant admittedAt,
        Instant dischargedAt,
        Integer stayDays,
        BigDecimal dailyRate,
        BigDecimal totalCharge,
        String dischargeSummary
) {}

