package com.apexcare.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for auto-generating a bill from an appointment.
 *
 * <p>Sent by the appointment-service via Feign, or by reception staff.
 * All monetary fields are optional — missing values default to zero.
 */
public class GenerateBillRequest {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @Size(max = 32, message = "uniquePatientId must be at most 32 characters")
    private String uniquePatientId;

    @NotNull(message = "Appointment ID is required")
    private UUID appointmentId;

    @DecimalMin(value = "0.00", inclusive = true, message = "Consultation fee cannot be negative")
    private BigDecimal consultationFee;

    @DecimalMin(value = "0.00", inclusive = true, message = "Medicine charges cannot be negative")
    private BigDecimal medicineCharges;

    @DecimalMin(value = "0.00", inclusive = true, message = "Lab charges cannot be negative")
    private BigDecimal labCharges;

    @DecimalMin(value = "0.00", inclusive = true, message = "Other charges cannot be negative")
    private BigDecimal otherCharges;

    @DecimalMin(value = "0.00", inclusive = true, message = "Discount percentage cannot be negative")
    @DecimalMax(value = "100.00", inclusive = true, message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage;

    @DecimalMin(value = "0.00", inclusive = true, message = "Tax percentage cannot be negative")
    @DecimalMax(value = "100.00", inclusive = true, message = "Tax percentage cannot exceed 100")
    private BigDecimal taxPercentage;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    // ---- getters / setters ----

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public BigDecimal getMedicineCharges() { return medicineCharges; }
    public void setMedicineCharges(BigDecimal medicineCharges) { this.medicineCharges = medicineCharges; }

    public BigDecimal getLabCharges() { return labCharges; }
    public void setLabCharges(BigDecimal labCharges) { this.labCharges = labCharges; }

    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }

    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }

    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
