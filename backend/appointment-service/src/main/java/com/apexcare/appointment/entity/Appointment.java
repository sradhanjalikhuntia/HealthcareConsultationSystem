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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "appointments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_appt_doctor_slot",
                        columnNames = {"doctorId", "appointmentDate", "startTime"})
        },
        indexes = {
                @Index(name = "idx_appt_doctor_date", columnList = "doctorId,appointmentDate"),
                @Index(name = "idx_appt_patient", columnList = "patientId"),
                @Index(name = "idx_appt_status", columnList = "status"),
                @Index(name = "idx_appt_date", columnList = "appointmentDate"),
                @Index(name = "idx_appt_created_at", columnList = "createdAt")
        }
)
public class Appointment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // ---- Patient snapshot ----

    @Column(nullable = false, updatable = false)
    private UUID patientId;

    @Column(length = 32, updatable = false)
    private String uniquePatientId;

    @Column(length = 160, updatable = false)
    private String patientName;

    // ---- Doctor snapshot ----

    @Column(nullable = false, updatable = false)
    private UUID doctorId;

    @Column(nullable = false, length = 120, updatable = false)
    private String doctorName;

    // ---- Scheduling ----

    @Column(nullable = false, updatable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(nullable = false, updatable = false)
    private LocalTime endTime;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(length = 4000)
    private String notes;

    // ---- Billing ----

    @Column(nullable = false)
    private boolean billingGenerated;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant billingGeneratedAt;

    // ---- Cancellation ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant cancelledAt;

    private UUID cancelledBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant calledAt;

    @Column(length = 500)
    private String cancellationReason;

    // ---- Audit ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = AppointmentStatus.SCHEDULED;
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalStateException(
                    "endTime must be strictly after startTime (got " + startTime + " → " + endTime + ")");
        }
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Appointment)) return false;
        Appointment other = (Appointment) o;
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

    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(UUID doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isBillingGenerated() { return billingGenerated; }
    public void setBillingGenerated(boolean billingGenerated) { this.billingGenerated = billingGenerated; }

    public Instant getBillingGeneratedAt() { return billingGeneratedAt; }
    public void setBillingGeneratedAt(Instant billingGeneratedAt) { this.billingGeneratedAt = billingGeneratedAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public UUID getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(UUID cancelledBy) { this.cancelledBy = cancelledBy; }

    public Instant getCalledAt() { return calledAt; }
    public void setCalledAt(Instant calledAt) { this.calledAt = calledAt; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
}
