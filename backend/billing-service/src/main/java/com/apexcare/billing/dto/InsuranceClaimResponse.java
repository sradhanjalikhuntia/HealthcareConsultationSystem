package com.apexcare.billing.dto;

import com.apexcare.billing.entity.InsuranceClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InsuranceClaimResponse(
        UUID id, String claimNumber, UUID patientId, UUID billId, String providerName,
        String policyNumber, BigDecimal claimedAmount, BigDecimal approvedAmount,
        BigDecimal deductibleAmount, BigDecimal patientCopay, String status, String remarks,
        Instant submittedAt, Instant reviewedAt, Instant settledAt) {
    public static InsuranceClaimResponse from(InsuranceClaim c) {
        return new InsuranceClaimResponse(c.getId(), c.getClaimNumber(), c.getPatientId(),
                c.getBillId(), c.getProviderName(), c.getPolicyNumber(), c.getClaimedAmount(),
                c.getApprovedAmount(), c.getDeductibleAmount(), c.getPatientCopay(), c.getStatus(),
                c.getRemarks(), c.getSubmittedAt(), c.getReviewedAt(), c.getSettledAt());
    }
}
