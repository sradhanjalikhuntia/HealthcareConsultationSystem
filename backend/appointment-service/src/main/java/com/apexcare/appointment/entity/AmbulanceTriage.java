package com.apexcare.appointment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ambulance_triage")
public class AmbulanceTriage {
    @Id
    private UUID id;
    private String patientName;
    private Integer patientAge;
    @Column(length = 1000)
    private String chiefComplaint;
    private String location;
    @Enumerated(EnumType.STRING)
    private EmergencySeverity severity;
    @Enumerated(EnumType.STRING)
    private AmbulanceStatus status;
    private UUID registeredBy;
    private Instant dispatchedAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (dispatchedAt == null) dispatchedAt = Instant.now();
        updatedAt = dispatchedAt;
        if (severity == null) severity = EmergencySeverity.HIGH;
        if (status == null) status = AmbulanceStatus.EN_ROUTE;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String value) { patientName = value; }
    public Integer getPatientAge() { return patientAge; }
    public void setPatientAge(Integer value) { patientAge = value; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String value) { chiefComplaint = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    public EmergencySeverity getSeverity() { return severity; }
    public void setSeverity(EmergencySeverity value) { severity = value; }
    public AmbulanceStatus getStatus() { return status; }
    public void setStatus(AmbulanceStatus value) { status = value; }
    public UUID getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(UUID value) { registeredBy = value; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
