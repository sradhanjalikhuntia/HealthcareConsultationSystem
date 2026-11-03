package com.apexcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload sent to the billing-service to generate a bill from a
 * completed or scheduled appointment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateBillRequest {

    @NotNull
    private UUID patientId;

    private String uniquePatientId;

    @NotNull
    private UUID appointmentId;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal consultationFee;

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }
}
