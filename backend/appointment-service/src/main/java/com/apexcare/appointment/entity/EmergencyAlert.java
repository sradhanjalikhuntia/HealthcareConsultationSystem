package com.apexcare.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emergency_code_alerts", indexes = {
        @Index(name = "idx_alert_active", columnList = "active"),
        @Index(name = "idx_alert_code_type", columnList = "codeType"),
        @Index(name = "idx_alert_triggered_at", columnList = "triggeredAt"),
        @Index(name = "idx_alert_active_triggered", columnList = "active,triggeredAt")
})
public class EmergencyAlert {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private EmergencyCodeType codeType;

    @Column(nullable = false, length = 100, updatable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private EmergencySeverity severity;

    @Column(updatable = false)
    private UUID triggeredBy;

    @Column(length = 80, updatable = false)
    private String triggeredByName;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 1000, updatable = false)
    private String notes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant triggeredAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant resolvedAt;

    private UUID resolvedBy;

    @Column(length = 1000)
    private String resolutionNotes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant updatedAt;

    @Version
    private Long version;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        this.triggeredAt = Instant.now();
        this.updatedAt = this.triggeredAt;
        this.active = true;
        this.resolvedAt = null;
        this.resolvedBy = null;
        if (this.severity == null) this.severity = EmergencySeverity.CRITICAL;
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (!this.active && this.resolvedAt == null) {
            throw new IllegalStateException(
                    "resolvedAt must be set when alert is no longer active");
        }
        if (this.active && this.resolvedAt != null) {
            throw new IllegalStateException(
                    "resolvedAt cannot be set while alert is still active");
        }
    }

    // ---------------------------------------------------------------
    // Domain methods
    // ---------------------------------------------------------------

    /** Marks the alert as resolved. Idempotent-safe: throws if already resolved. */
    public void resolve(UUID resolvedByUserId, String resolutionNotes) {
        if (!this.active) {
            throw new IllegalStateException("Alert is already resolved");
        }
        this.active = false;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedByUserId;
        this.resolutionNotes = resolutionNotes;
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmergencyAlert)) return false;
        EmergencyAlert other = (EmergencyAlert) o;
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

    public EmergencyCodeType getCodeType() { return codeType; }
    public void setCodeType(EmergencyCodeType codeType) { this.codeType = codeType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public EmergencySeverity getSeverity() { return severity; }
    public void setSeverity(EmergencySeverity severity) { this.severity = severity; }

    public UUID getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(UUID triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getTriggeredByName() { return triggeredByName; }
    public void setTriggeredByName(String triggeredByName) { this.triggeredByName = triggeredByName; }

    public boolean isActive() { return active; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getTriggeredAt() { return triggeredAt; }

    public Instant getResolvedAt() { return resolvedAt; }

    public UUID getResolvedBy() { return resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
}
