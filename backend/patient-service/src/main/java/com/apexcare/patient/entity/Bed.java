package com.apexcare.patient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hospital_beds", indexes = {
        @Index(name = "idx_bed_status", columnList = "status"),
        @Index(name = "idx_bed_ward", columnList = "wardType"),
        @Index(name = "idx_bed_patient", columnList = "currentPatientId")
})
public class Bed {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32, updatable = false)
    private String bedNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private WardType wardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BedStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyRate;

    // ---- Current occupancy ----

    private UUID currentPatientId;

    /**
     * Snapshot of patient's full name at admission time.
     * Kept denormalized for fast ward-display queries.
     * Not updated if the patient later changes their name.
     */
    @Column(length = 160)
    private String currentPatientName;

    // (currentDiagnosis removed — see AdmissionRecord for clinical data)

    // ---- Audit / lifecycle timestamps ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant lastOccupiedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant lastDischargedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant lastSanitizedAt;

    private UUID lastSanitizedBy;

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
        if (this.status == null) this.status = BedStatus.AVAILABLE;
        if (this.dailyRate == null) {
            throw new IllegalStateException(
                    "dailyRate must be set explicitly before persisting a Bed");
        }
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (this.status == BedStatus.OCCUPIED) {
            if (this.currentPatientId == null) {
                throw new IllegalStateException(
                        "Bed " + bedNumber + " is OCCUPIED but has no patient assigned");
            }
        } else {
            if (this.currentPatientId != null) {
                throw new IllegalStateException(
                        "Bed " + bedNumber + " is " + status + " but has a patient assigned");
            }
        }
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bed)) return false;
        Bed other = (Bed) o;
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
    public String getBedNumber() { return bedNumber; }
    public void setBedNumber(String bedNumber) { this.bedNumber = bedNumber; }
    public WardType getWardType() { return wardType; }
    public void setWardType(WardType wardType) { this.wardType = wardType; }
    public BedStatus getStatus() { return status; }
    public void setStatus(BedStatus status) { this.status = status; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public UUID getCurrentPatientId() { return currentPatientId; }
    public void setCurrentPatientId(UUID currentPatientId) { this.currentPatientId = currentPatientId; }
    public String getCurrentPatientName() { return currentPatientName; }
    public void setCurrentPatientName(String currentPatientName) { this.currentPatientName = currentPatientName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastOccupiedAt() { return lastOccupiedAt; }
    public void setLastOccupiedAt(Instant lastOccupiedAt) { this.lastOccupiedAt = lastOccupiedAt; }
    public Instant getLastDischargedAt() { return lastDischargedAt; }
    public void setLastDischargedAt(Instant lastDischargedAt) { this.lastDischargedAt = lastDischargedAt; }
    public Instant getLastSanitizedAt() { return lastSanitizedAt; }
    public void setLastSanitizedAt(Instant lastSanitizedAt) { this.lastSanitizedAt = lastSanitizedAt; }
    public UUID getLastSanitizedBy() { return lastSanitizedBy; }
    public void setLastSanitizedBy(UUID lastSanitizedBy) { this.lastSanitizedBy = lastSanitizedBy; }
    public Long getVersion() { return version; }
}
