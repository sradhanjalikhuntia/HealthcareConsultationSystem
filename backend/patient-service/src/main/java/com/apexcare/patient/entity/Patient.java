package com.apexcare.patient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patients_name", columnList = "lastName,firstName"),
        @Index(name = "idx_patients_email", columnList = "email"),
        @Index(name = "idx_patients_phone", columnList = "phone"),
        @Index(name = "idx_patients_status", columnList = "status"),
        @Index(name = "idx_patients_unique_id", columnList = "uniquePatientId"),
        @Index(name = "idx_patients_dob", columnList = "dateOfBirth"),
        @Index(name = "idx_patients_created_at", columnList = "createdAt")
})
public class Patient {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Hospital-assigned patient identifier (e.g. ACP-2026-000001).
     * Assigned by the server on first activation; immutable thereafter.
     */
    @Column(unique = true, length = 32, updatable = false)
    private String uniquePatientId;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, updatable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BloodGroup bloodGroup;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 160, unique = true)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(length = 120)
    private String emergencyContact;

    /**
     * PHI — should be encrypted at rest via AttributeConverter in production.
     */
    @Column(length = 2000)
    private String medicalHistory;

    /**
     * PHI — should be encrypted at rest via AttributeConverter in production.
     */
    @Column(length = 1000)
    private String allergies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status = PatientStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // ---------------------------------------------------------------
    // Lifecycle callbacks
    // ---------------------------------------------------------------

    @PrePersist
    void onCreate() {
        // Server-authoritative — always overwrite
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = PatientStatus.ACTIVE;
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (this.dateOfBirth != null && this.dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalStateException("dateOfBirth cannot be in the future");
        }
        if (this.status == PatientStatus.ACTIVE && this.uniquePatientId == null) {
            throw new IllegalStateException(
                    "ACTIVE patient must have a uniquePatientId assigned");
        }
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient)) return false;
        Patient other = (Patient) o;
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
    public void setId(UUID id) { this.id = id; }
    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase().trim();
    }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public PatientStatus getStatus() { return status; }
    public void setStatus(PatientStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
