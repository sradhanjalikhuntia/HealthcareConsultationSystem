package com.apexcare.patient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admission_records", indexes = {
        @Index(name = "idx_admission_patient", columnList = "patientId"),
        @Index(name = "idx_admission_bed", columnList = "bedId"),
        @Index(name = "idx_admission_status", columnList = "status"),
        @Index(name = "idx_admission_admitted_at", columnList = "admittedAt")
})
public class AdmissionRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID patientId;

    @Column(nullable = false, updatable = false)
    private UUID bedId;

    private UUID admittingDoctorId;

    // ---- Snapshots (immutable, for audit integrity) ----

    @Column(nullable = false, length = 32, updatable = false)
    private String bedNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private WardType wardType;

    @Column(nullable = false, length = 160, updatable = false)
    private String patientName;    // snapshot at admission time

    // ---- Timestamps ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant admittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant dischargedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant updatedAt;

    // ---- Clinical text ----

    @Column(length = 1000)
    private String admissionReason;

    @Column(columnDefinition = "TEXT")
    private String dischargeSummary;

    // ---- Status ----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdmissionStatus status;

    // ---- Billing metadata ----

    @Column(name = "daily_rate", precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "stay_days")
    private Integer stayDays;

    @Column(name = "total_charge", precision = 12, scale = 2)
    private BigDecimal totalCharge;

    @Column(name = "billed")
    private Boolean billed = false;

    @Column(name = "bill_id")
    private UUID billId;

    // ---- Audit ----

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    private UUID updatedBy;

    @Version
    private Long version;

    // ---- Lifecycle callbacks ----

    @PrePersist
    void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.admittedAt == null) this.admittedAt = Instant.now();
        if (this.status == null) this.status = AdmissionStatus.ADMITTED;
        if (this.billed == null) this.billed = false;

        if (this.patientName == null || this.bedNumber == null || this.wardType == null) {
            throw new IllegalStateException(
                    "Snapshot fields (patientName, bedNumber, wardType) must be set before persist");
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();

        if (this.status == AdmissionStatus.DISCHARGED && this.dischargedAt == null) {
            throw new IllegalStateException(
                    "dischargedAt must be set when status is DISCHARGED");
        }
        if (this.status != AdmissionStatus.DISCHARGED && this.dischargedAt != null) {
            throw new IllegalStateException(
                    "dischargedAt can only be set when status is DISCHARGED");
        }
    }

    // ---- equals / hashCode ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdmissionRecord)) return false;
        AdmissionRecord other = (AdmissionRecord) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public UUID getBedId() { return bedId; }
    public void setBedId(UUID bedId) { this.bedId = bedId; }
    public UUID getAdmittingDoctorId() { return admittingDoctorId; }
    public void setAdvocatingDoctorId(UUID id) { this.admittingDoctorId = id; }
    public void setAdmittingDoctorId(UUID id) { this.admittingDoctorId = id; }
    public String getBedNumber() { return bedNumber; }
    public void setBedNumber(String bedNumber) { this.bedNumber = bedNumber; }
    public WardType getWardType() { return wardType; }
    public void setWardType(WardType wardType) { this.wardType = wardType; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public Instant getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(Instant admittedAt) { this.admittedAt = admittedAt; }
    public Instant getDischargedAt() { return dischargedAt; }
    public void setDischargedAt(Instant dischargedAt) { this.dischargedAt = dischargedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getAdmissionReason() { return admissionReason; }
    public void setAdmissionReason(String admissionReason) { this.admissionReason = admissionReason; }
    public String getDischargeSummary() { return dischargeSummary; }
    public void setDischargeSummary(String dischargeSummary) { this.dischargeSummary = dischargeSummary; }
    public AdmissionStatus getStatus() { return status; }
    public void setStatus(AdmissionStatus status) { this.status = status; }

    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public Integer getStayDays() { return stayDays; }
    public void setStayDays(Integer stayDays) { this.stayDays = stayDays; }
    public BigDecimal getTotalCharge() { return totalCharge; }
    public void setTotalCharge(BigDecimal totalCharge) { this.totalCharge = totalCharge; }
    public Boolean getBilled() { return billed; }
    public void setBilled(Boolean billed) { this.billed = billed; }
    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
}
