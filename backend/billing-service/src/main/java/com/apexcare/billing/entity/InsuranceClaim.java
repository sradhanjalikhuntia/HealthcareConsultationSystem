package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "insurance_claims",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_claims_claim_number", columnNames = "claim_number")
        },
        indexes = {
                @Index(name = "idx_claims_patient", columnList = "patient_id"),
                @Index(name = "idx_claims_bill", columnList = "bill_id"),
                @Index(name = "idx_claims_status", columnList = "status"),
                @Index(name = "idx_claims_provider", columnList = "provider_name"),
                @Index(name = "idx_claims_submitted_at", columnList = "submitted_at"),
                @Index(name = "idx_claims_status_submitted", columnList = "status,submitted_at")
        }
)
public class InsuranceClaim {

    // Valid status values — enforced at persist time
    public static final List<String> VALID_STATUSES = List.of(
            "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "SETTLED");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 32, updatable = false)
    private String claimNumber;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "bill_id", updatable = false)
    private UUID billId;

    @Column(name = "provider_name", nullable = false, length = 100, updatable = false)
    private String providerName;

    @Column(name = "policy_number", nullable = false, length = 64, updatable = false)
    private String policyNumber;

    // ---- Amounts ----

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal claimedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    @Column(name = "deductible_amount", precision = 12, scale = 2)
    private BigDecimal deductibleAmount = BigDecimal.ZERO;

    @Column(name = "patient_copay", precision = 12, scale = 2)
    private BigDecimal patientCopay = BigDecimal.ZERO;

    // ---- Status ----

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(length = 2000)
    private String remarks;

    // ---- Timestamps ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "settled_at")
    private Instant settledAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---- Audit ----

    private UUID submittedBy;
    private UUID reviewedBy;

    @Version
    private Long version;

    // =================================================================
    // Lifecycle
    // =================================================================

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        this.claimNumber = generateClaimNumber();
        Instant now = Instant.now();
        this.submittedAt = now;
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null)           this.status = "SUBMITTED";
        if (this.approvedAmount == null)   this.approvedAmount = BigDecimal.ZERO;
        if (this.deductibleAmount == null) this.deductibleAmount = BigDecimal.ZERO;
        if (this.patientCopay == null)     this.patientCopay = BigDecimal.ZERO;

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
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "Invalid status: " + status + ". Allowed: " + VALID_STATUSES);
        }
        requireNonNegative(claimedAmount,    "claimedAmount");
        requireNonNegative(approvedAmount,   "approvedAmount");
        requireNonNegative(deductibleAmount, "deductibleAmount");
        requireNonNegative(patientCopay,     "patientCopay");

        if (claimedAmount != null && claimedAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("claimedAmount must be greater than zero");
        }
    }

    private void validateInvariants() {
        boolean approvedOrSettled = "APPROVED".equals(status) || "SETTLED".equals(status);

        if (approvedOrSettled) {
            BigDecimal sum = nz(approvedAmount)
                    .add(nz(deductibleAmount))
                    .add(nz(patientCopay));
            if (sum.compareTo(nz(claimedAmount)) > 0) {
                throw new IllegalStateException(
                        "approved + deductible + patientCopay (" + sum
                                + ") cannot exceed claimedAmount (" + claimedAmount + ")");
            }
        }

        if ("SETTLED".equals(status) && settledAt == null) {
            throw new IllegalStateException(
                    "SETTLED claim must have settledAt timestamp");
        }
        if (!"SETTLED".equals(status) && settledAt != null) {
            throw new IllegalStateException(
                    "settledAt can only be set when status is SETTLED");
        }
    }

    // =================================================================
    // Domain transitions
    // =================================================================

    public void startReview(UUID reviewedByUserId) {
        requireTransitionTo("UNDER_REVIEW");
        this.status = "UNDER_REVIEW";
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewedByUserId;
    }

    public void approve(BigDecimal approvedAmount,
                        BigDecimal deductibleAmount,
                        BigDecimal patientCopay,
                        String remarks,
                        UUID reviewedByUserId) {
        requireTransitionTo("APPROVED");

        this.status = "APPROVED";
        this.approvedAmount   = nz(approvedAmount).setScale(2, RoundingMode.HALF_UP);
        this.deductibleAmount = nz(deductibleAmount).setScale(2, RoundingMode.HALF_UP);
        this.patientCopay     = nz(patientCopay).setScale(2, RoundingMode.HALF_UP);
        this.remarks          = remarks;
        this.reviewedAt       = Instant.now();
        this.reviewedBy       = reviewedByUserId;
    }

    public void reject(String remarks, UUID reviewedByUserId) {
        requireTransitionTo("REJECTED");
        this.status = "REJECTED";
        this.remarks = remarks;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewedByUserId;
    }

    public void settle() {
        requireTransitionTo("SETTLED");
        this.status = "SETTLED";
        this.settledAt = Instant.now();
    }

    private void requireTransitionTo(String target) {
        String from = this.status;
        boolean ok = switch (from) {
            case "SUBMITTED"    -> target.equals("UNDER_REVIEW") || target.equals("REJECTED");
            case "UNDER_REVIEW" -> target.equals("APPROVED")     || target.equals("REJECTED");
            case "APPROVED"     -> target.equals("SETTLED");
            case "REJECTED", "SETTLED" -> false;   // terminal
            default -> false;
        };
        if (!ok) {
            throw new IllegalStateException(
                    "Invalid claim status transition: " + from + " → " + target);
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    private static String generateClaimNumber() {
        int year = java.time.Year.now(java.time.ZoneOffset.UTC).getValue();
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "CLM-" + year + "-" + suffix;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static void requireNonNegative(BigDecimal v, String field) {
        if (v != null && v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    // =================================================================
    // equals / hashCode
    // =================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsuranceClaim)) return false;
        InsuranceClaim other = (InsuranceClaim) o;
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

    public String getClaimNumber() { return claimNumber; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }

    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public void setClaimedAmount(BigDecimal claimedAmount) { this.claimedAmount = claimedAmount; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public BigDecimal getDeductibleAmount() { return deductibleAmount; }
    public void setDeductibleAmount(BigDecimal deductibleAmount) { this.deductibleAmount = deductibleAmount; }
    public BigDecimal getPatientCopay() { return patientCopay; }
    public void setPatientCopay(BigDecimal patientCopay) { this.patientCopay = patientCopay; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }

    public UUID getReviewedBy() { return reviewedBy; }

    public Long getVersion() { return version; }
}
