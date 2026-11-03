package com.apexcare.patient.dto;

import com.apexcare.patient.entity.DoctorReferral;
import java.time.Instant;
import java.util.UUID;

public record ReferralResponse(UUID id, UUID patientId, UUID referringDoctorId,
        String referringDoctorName, String targetSpecialty, UUID referredDoctorId,
        String referredDoctorName, String priority, String clinicalSummary,
        String referralQuestion, String status, Instant createdAt, Instant acceptedAt,
        Instant completedAt, String declineReason, String completionNotes) {
    public static ReferralResponse from(DoctorReferral r) {
        return new ReferralResponse(r.getId(), r.getPatientId(), r.getReferringDoctorId(),
                r.getReferringDoctorName(), r.getTargetSpecialty() == null ? null : r.getTargetSpecialty().name(),
                r.getReferredDoctorId(), r.getReferredDoctorName(),
                r.getPriority() == null ? null : r.getPriority().name(), r.getClinicalSummary(),
                r.getReferralQuestion(), r.getStatus() == null ? null : r.getStatus().name(),
                r.getCreatedAt(), r.getAcceptedAt(), r.getCompletedAt(),
                r.getDeclineReason(), r.getCompletionNotes());
    }
}
