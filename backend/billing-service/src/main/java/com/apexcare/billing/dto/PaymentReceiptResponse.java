package com.apexcare.billing.dto;

import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.Payment;
import com.apexcare.billing.entity.PaymentMethod;
import com.apexcare.billing.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Printable payment receipt combining bill line items and payment details.
 *
 * <p>Field values are snapshotted from the {@link Bill} and {@link Payment}
 * at receipt-generation time — later edits to either entity do not affect
 * this receipt (financial audit integrity).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentReceiptResponse {

    // ---- Receipt identity ----

    private UUID receiptId;
    private UUID billId;
    private UUID paymentId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant receiptIssuedAt;

    // ---- Patient / appointment ----

    private UUID patientId;
    private String uniquePatientId;
    private UUID appointmentId;

    // ---- Payment ----

    private BigDecimal amountPaid;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String transactionReference;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant paymentDate;

    // ---- Clinic info (configurable) ----

    private String clinicName;
    private String clinicTaxId;
    private String clinicAddress;
    private String clinicPhone;

    // ---- Bill line items ----

    private BigDecimal consultationFee;
    private BigDecimal medicineCharges;
    private BigDecimal labCharges;
    private BigDecimal otherCharges;

    // ---- Bill totals ----

    private BigDecimal subtotalAmount;
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private BigDecimal taxPercentage;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    // =================================================================
    // Factory
    // =================================================================

    public static PaymentReceiptResponse from(Bill bill, Payment payment) {
        if (bill == null || payment == null) return null;

        PaymentReceiptResponse r = new PaymentReceiptResponse();

        // Receipt / payment identity
        r.receiptId = payment.getId();
        r.paymentId = payment.getId();
        r.billId = bill.getId();
        r.receiptIssuedAt = Instant.now();

        // Patient / appointment
        r.patientId = bill.getPatientId();
        r.uniquePatientId = bill.getUniquePatientId();
        r.appointmentId = bill.getAppointmentId();

        // Payment
        r.amountPaid = payment.getAmount();
        r.paymentMethod = payment.getPaymentMethod();
        r.status = payment.getStatus();
        r.transactionReference = payment.getTransactionReference();
        r.paymentDate = payment.getPaymentDate();

        // Line items
        r.consultationFee = bill.getConsultationFee();
        r.medicineCharges = bill.getMedicineCharges();
        r.labCharges = bill.getLabCharges();
        r.otherCharges = bill.getOtherCharges();

        // Totals
        r.subtotalAmount = bill.getSubtotalAmount();
        r.discountPercentage = bill.getDiscountPercentage();
        r.discountAmount = bill.getDiscountAmount();
        r.taxPercentage = bill.getTaxPercentage();
        r.taxAmount = bill.getTaxAmount();
        r.totalAmount = bill.getTotalAmount();

        return r;
    }

    // =================================================================
    // Getters / setters
    // =================================================================

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public Instant getReceiptIssuedAt() { return receiptIssuedAt; }
    public void setReceiptIssuedAt(Instant receiptIssuedAt) { this.receiptIssuedAt = receiptIssuedAt; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public Instant getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Instant paymentDate) { this.paymentDate = paymentDate; }

    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }

    public String getClinicTaxId() { return clinicTaxId; }
    public void setClinicTaxId(String clinicTaxId) { this.clinicTaxId = clinicTaxId; }

    public String getClinicAddress() { return clinicAddress; }
    public void setClinicAddress(String clinicAddress) { this.clinicAddress = clinicAddress; }

    public String getClinicPhone() { return clinicPhone; }
    public void setClinicPhone(String clinicPhone) { this.clinicPhone = clinicPhone; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public BigDecimal getMedicineCharges() { return medicineCharges; }
    public void setMedicineCharges(BigDecimal medicineCharges) { this.medicineCharges = medicineCharges; }

    public BigDecimal getLabCharges() { return labCharges; }
    public void setLabCharges(BigDecimal labCharges) { this.labCharges = labCharges; }

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
}
