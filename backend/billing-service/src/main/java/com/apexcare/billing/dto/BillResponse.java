package com.apexcare.billing.dto;

import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.PaymentMethod;
import com.apexcare.billing.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillResponse {

    private UUID id;
    private UUID patientId;
    private String uniquePatientId;
    private UUID appointmentId;

    // ---- Line items ----

    private BigDecimal consultationFee;
    private BigDecimal medicineCharges;
    private BigDecimal labCharges;
    private BigDecimal wardCharges;
    private BigDecimal otherCharges;

    // ---- Computed totals ----

    private BigDecimal subtotalAmount;
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private BigDecimal taxPercentage;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    // ---- Payment ----

    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private String transactionReference;

    private String notes;

    // ---- Timestamps ----

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant paidAt;

    public static BillResponse from(Bill bill) {
        if (bill == null) return null;

        BillResponse dto = new BillResponse();
        dto.id = bill.getId();
        dto.patientId = bill.getPatientId();
        dto.uniquePatientId = bill.getUniquePatientId();
        dto.appointmentId = bill.getAppointmentId();

        dto.consultationFee = bill.getConsultationFee();
        dto.medicineCharges = bill.getMedicineCharges();
        dto.labCharges = bill.getLabCharges();
        dto.wardCharges = bill.getWardCharges();
        dto.otherCharges = bill.getOtherCharges();

        dto.subtotalAmount = bill.getSubtotalAmount();
        dto.discountPercentage = bill.getDiscountPercentage();
        dto.discountAmount = bill.getDiscountAmount();
        dto.taxPercentage = bill.getTaxPercentage();
        dto.taxAmount = bill.getTaxAmount();
        dto.totalAmount = bill.getTotalAmount();

        dto.paymentStatus = bill.getPaymentStatus();
        dto.paymentMethod = bill.getPaymentMethod();
        dto.transactionReference = bill.getTransactionReference();
        dto.notes = bill.getNotes();

        dto.createdAt = bill.getCreatedAt();
        dto.updatedAt = bill.getUpdatedAt();
        dto.paidAt = bill.getPaidAt();

        return dto;
    }

    // =================================================================
    // Getters / setters
    // =================================================================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }

    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
