package com.apexcare.billing.dto;

import com.apexcare.billing.entity.LabTestOrder;
import java.time.Instant;
import java.util.UUID;

public record LabTestOrderResponse(
        UUID id, UUID patientId, UUID doctorId, UUID appointmentId, String testName,
        String category, String status, String priority, String sampleId, String resultSummary,
        String referenceRange, String abnormalFlag, String resultFileUrl, Instant orderedAt,
        Instant completedAt, Instant resultReportedAt) {
    public static LabTestOrderResponse from(LabTestOrder o) {
        return new LabTestOrderResponse(o.getId(), o.getPatientId(), o.getDoctorId(),
                o.getAppointmentId(), o.getTestName(), o.getCategory(), o.getStatus(),
                o.getPriority(), o.getSampleId(), o.getResultSummary(), o.getReferenceRange(),
                o.getAbnormalFlag(), o.getResultFileUrl(), o.getOrderedAt(), o.getCompletedAt(),
                o.getResultReportedAt());
    }
}
