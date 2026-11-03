package com.apexcare.patient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_referrals", indexes = {
        @Index(name = "idx_referral_patient", columnList = "patientId"),
        @Index(name = "idx_referral_referring", columnList = "referringDoctorId"),
        @Index(name = "idx_referral_referred", columnList = "referredDoctorId"),
        @Index(name = "idx_referral_status", columnList = "status"),
        @Index(name = "idx_referral_status_priority", columnList = "status,priority")
})
public class DoctorReferral {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID patientId;

    @Column(nullable = false, updatable = false)
    private UUID referringDoctorId;

    /**
     * Snapshot of the referring doctor's display name at creation time.
     * Immutable — preserved for historical accuracy.
     */
    @Column(length = 100, updatable = false)
    private String referringDoctorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64, updatable = false)
    private Specialty targetSpecialty;

    /**
     * Populated only when a doctor accepts the referral.
     */
    private UUID referredDoctorId;

    @Column(length = 100)
    private String referredDoctorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ReferralPriority priority;

    @Column(columnDefinition = "TEXT")
    private String clinicalSummary;

    @Column(length = 2000)
    private String referralQuestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReferralStatus status;

    // ---- Workflow timestamps ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant acceptedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant updatedAt;

    // ---- Decline / completion details ----

    @Column(length = 500)
    private String declineReason;

    @Column(length = 2000)
    private String completionNotes;

    // ---- Optimistic locking ----

    @Version
    private Long version;

    // ---------------------------------------------------------------
    // Lifecycle callbacks
    // ---------------------------------------------------------------

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        if (this.status == null) this.status = ReferralStatus.PENDING;
        if (this.priority == null) this.priority = ReferralPriority.ROUTINE;
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();

        if (this.status == ReferralStatus.ACCEPTED && this.acceptedAt == null) {
            this.acceptedAt = Instant.now();
        }
        if (this.status == ReferralStatus.COMPLETED && this.completedAt == null) {
            this.completedAt = Instant.now();
        }
        validateInvariants();
    }

    private void validateInvariants() {
        switch (this.status) {
            case PENDING -> {
                if (this.referredDoctorId != null || this.acceptedAt != null) {
                    throw new IllegalStateException(
                            "PENDING referral cannot have a referred doctor or acceptance time");
                }
            }
            case ACCEPTED, COMPLETED -> {
                if (this.referredDoctorId == null) {
                    throw new IllegalStateException(
                            this.status + " referral must have a referred doctor assigned");
                }
            }
            case DECLINED -> {
                if (this.declineReason == null || this.declineReason.isBlank()) {
                    throw new IllegalStateException(
                            "DECLINED referral must have a decline reason");
                }
            }
            default -> { /* no-op */ }
        }

        if (this.status == ReferralStatus.COMPLETED && this.completedAt == null) {
            throw new IllegalStateException(
                    "COMPLETED referral must have completedAt timestamp");
        }
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoctorReferral)) return false;
        DoctorReferral other = (DoctorReferral) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ---------------------------------------------------------------
    // Getters / setters
    // ---------------------------------------------------------------

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public UUID getReferringDoctorId() { return referringDoctorId; }
    public void setReferringDoctorId(UUID referringDoctorId) { this.referringDoctorId = referringDoctorId; }
    public String getReferringDoctorName() { return referringDoctorName; }
    public void setReferringDoctorName(String referringDoctorName) { this.referringDoctorName = referringDoctorName; }
    public Specialty getTargetSpecialty() { return targetSpecialty; }
    public void setTargetSpecialty(Specialty targetSpecialty) { this.targetSpecialty = targetSpecialty; }
    public UUID getReferredDoctorId() { return referredDoctorId; }
    public void setReferredDoctorId(UUID referredDoctorId) { this.referredDoctorId = referredDoctorId; }
    public String getReferredDoctorName() { return referredDoctorName; }
    public void setReferredDoctorName(String referredDoctorName) { this.referredDoctorName = referredDoctorName; }
    public ReferralPriority getPriority() { return priority; }
    public void setPriority(ReferralPriority priority) { this.priority = priority; }
    public String getClinicalSummary() { return clinicalSummary; }
    public void setClinicalSummary(String clinicalSummary) { this.clinicalSummary = clinicalSummary; }
    public String getReferralQuestion() { return referralQuestion; }
    public void setReferralQuestion(String referralQuestion) { this.referralQuestion = referralQuestion; }
    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public Long getVersion() { return version; }
}
