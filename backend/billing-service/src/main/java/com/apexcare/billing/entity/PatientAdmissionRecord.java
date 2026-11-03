package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "patient_admission_records",
        indexes = {
                @Index(name = "idx_adm_patient", columnList = "patient_id"),
                @Index(name = "idx_adm_admission", columnList = "admission_id"),
                @Index(name = "idx_adm_billed", columnList = "billed"),
                @Index(name = "idx_adm_discharged", columnList = "discharged_at")
        }
)
public class PatientAdmissionRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "patient_name", length = 160)
    private String patientName;

    @Column(name = "bed_number", nullable = false, length = 32)
    private String bedNumber;

    @Column(name = "ward_type", nullable = false, length = 32)
    private String wardType;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "discharged_at", nullable = false)
    private Instant dischargedAt;

    @Column(name = "stay_days", nullable = false)
    private Integer stayDays;

    @Column(name = "daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discharge_summary", length = 1000)
    private String dischargeSummary;

    @Column(nullable = false)
    private Boolean billed = false;

    @Column(name = "bill_id")
    private UUID billId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.billed == null) this.billed = false;
        if (this.totalAmount == null && this.dailyRate != null && this.stayDays != null) {
            this.totalAmount = this.dailyRate.multiply(BigDecimal.valueOf(this.stayDays));
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAdmissionId() { return admissionId; }
    public void setAdmissionId(UUID admissionId) { this.admissionId = admissionId; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getBedNumber() { return bedNumber; }
    public void setBedNumber(String bedNumber) { this.bedNumber = bedNumber; }

    public String getWardType() { return wardType; }
    public void setWardType(String wardType) { this.wardType = wardType; }

    public Instant getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(Instant admittedAt) { this.admittedAt = admittedAt; }

    public Instant getDischargedAt() { return dischargedAt; }
    public void setDischargedAt(Instant dischargedAt) { this.dischargedAt = dischargedAt; }

    public Integer getStayDays() { return stayDays; }
    public void setStayDays(Integer stayDays) { this.stayDays = stayDays; }

    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getDischargeSummary() { return dischargeSummary; }
    public void setDischargeSummary(String dischargeSummary) { this.dischargeSummary = dischargeSummary; }

    public Boolean getBilled() { return billed; }
    public void setBilled(Boolean billed) { this.billed = billed; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

