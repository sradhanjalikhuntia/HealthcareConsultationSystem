package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payments_txn_ref",
                        columnNames = {"transaction_reference"})
        },
        indexes = {
                @Index(name = "idx_payments_bill_id", columnList = "bill_id"),
                @Index(name = "idx_payments_patient_id", columnList = "patient_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_method", columnList = "payment_method"),
                @Index(name = "idx_payments_payment_date", columnList = "payment_date"),
                @Index(name = "idx_payments_bill_status", columnList = "bill_id,status")
        }
)
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private UUID billId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32, updatable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_reference", nullable = false, length = 80, updatable = false)
    private String transactionReference;

    @Column(name = "gateway_response", length = 2000, updatable = false)
    private String gatewayResponse;

    @Column(length = 2000)
    private String notes;

    // ---- Timestamps ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "payment_date", nullable = false, updatable = false)
    private Instant paymentDate;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---- Refund tracking ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    private UUID refundedBy;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    // ---- Audit ----

    private UUID createdBy;

    @Version
    private Long version;

    // =================================================================
    // Lifecycle
    // =================================================================

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        Instant now = Instant.now();
        this.paymentDate = now;
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) this.status = PaymentStatus.PENDING;
        if (this.refundedAmount == null) this.refundedAmount = BigDecimal.ZERO;

        validateInputs();
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInputs();
        validateInvariants();
    }

    // =================================================================
    // Validation
    // =================================================================

    private void validateInputs() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (refundedAmount != null && refundedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("refundedAmount cannot be negative");
        }
        if (refundedAmount != null && refundedAmount.compareTo(amount) > 0) {
            throw new IllegalArgumentException(
                    "refundedAmount (" + refundedAmount + ") cannot exceed amount (" + amount + ")");
        }
    }

    private void validateInvariants() {
        if (status == PaymentStatus.REFUNDED && refundedAt == null) {
            throw new IllegalStateException(
                    "REFUNDED payment must have refundedAt timestamp");
        }
        if (status != PaymentStatus.REFUNDED && refundedAt != null) {
            throw new IllegalStateException(
                    "refundedAt can only be set when status is REFUNDED");
        }
    }

    // =================================================================
    // Domain transitions
    // =================================================================

    /** Marks the payment as PAID. Idempotent — safe to call multiple times. */
    public void markPaid() {
        if (status == PaymentStatus.PAID) return;
        status.requireTransitionTo(PaymentStatus.PAID);
        this.status = PaymentStatus.PAID;
    }

    public void markFailed(String gatewayResponse) {
        status.requireTransitionTo(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        if (gatewayResponse != null) {
            this.gatewayResponse = gatewayResponse;
        }
    }

    public void refund(BigDecimal amount, String reason, UUID refundedByUserId) {
        status.requireTransitionTo(PaymentStatus.REFUNDED);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (amount.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount cannot exceed original payment amount");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        this.refundReason = reason;
        this.refundedAt = Instant.now();
        this.refundedBy = refundedByUserId;
    }

    // =================================================================
    // equals / hashCode
    // =================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment)) return false;
        Payment other = (Payment) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // =================================================================
    // Getters / setters
    // =================================================================

    public UUID getId() { return id; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getStatus() { return status; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getGatewayResponse() { return gatewayResponse; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getPaymentDate() { return paymentDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getRefundedAt() { return refundedAt; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public UUID getRefundedBy() { return refundedBy; }
    public String getRefundReason() { return refundReason; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Long getVersion() { return version; }
}
