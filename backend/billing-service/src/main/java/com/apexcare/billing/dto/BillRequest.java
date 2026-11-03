package com.apexcare.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class BillRequest {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @Size(max = 32, message = "uniquePatientId must be at most 32 characters")
    private String uniquePatientId;

    private UUID appointmentId;

    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Consultation fee cannot be negative")
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Medicine charges cannot be negative")
    private BigDecimal medicineCharges = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Lab charges cannot be negative")
    private BigDecimal labCharges = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Ward charges cannot be negative")
    private BigDecimal wardCharges = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Other charges cannot be negative")
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Discount percentage cannot be negative")
    @DecimalMax(value = "100.00", inclusive = true, message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", inclusive = true, message = "Tax percentage cannot be negative")
    @DecimalMax(value = "100.00", inclusive = true, message = "Tax percentage cannot exceed 100")
    private BigDecimal taxPercentage = BigDecimal.ZERO;

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

    public BigDecimal getWardCharges() { return wardCharges; }
    public void setWardCharges(BigDecimal wardCharges) { this.wardCharges = wardCharges; }

    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }

    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }

    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
