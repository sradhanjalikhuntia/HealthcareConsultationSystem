package com.apexcare.auth.entity;

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
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_role", columnList = "role"),
        @Index(name = "idx_users_enabled", columnList = "enabled"),
        @Index(name = "idx_users_linked_patient", columnList = "linkedPatientId"),
        @Index(name = "idx_users_doctor_code", columnList = "doctorCode")
})
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 80, unique = true)
    private String username;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    /**
     * BCrypt hash of the user's password. Never store plaintext.
     * Never log this field.
     */
    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    /**
     * Set only for PATIENT role users — links the login account to a
     * patient record in the patient-service.
     */
    @Column(name = "linked_patient_id")
    private UUID linkedPatientId;

    /**
     * Set only for DOCTOR role users — hospital-assigned doctor code.
     */
    @Column(name = "doctor_code", length = 32)
    private String doctorCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

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
        if (this.lastLoginAt == null) {
            this.lastLoginAt = now;
        }
        if (this.role == null) {
            throw new IllegalStateException("User.role is required");
        }
        validateInvariants();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (this.role == Role.PATIENT && this.linkedPatientId == null) {
            throw new IllegalStateException(
                    "PATIENT role requires a linkedPatientId");
        }
        if (this.role != Role.PATIENT && this.linkedPatientId != null) {
            throw new IllegalStateException(
                    "linkedPatientId only valid for PATIENT role (got " + this.role + ")");
        }
        if (this.role == Role.DOCTOR && (this.doctorCode == null || this.doctorCode.isBlank())) {
            throw new IllegalStateException(
                    "DOCTOR role requires a doctorCode");
        }
        if (this.role != Role.DOCTOR && this.doctorCode != null) {
            throw new IllegalStateException(
                    "doctorCode only valid for DOCTOR role (got " + this.role + ")");
        }
    }

    // ---------------------------------------------------------------
    // Domain methods
    // ---------------------------------------------------------------

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public void disable() {
        if (!this.enabled) return;
        this.enabled = false;
    }

    public void enable() {
        if (this.enabled) return;
        this.enabled = true;
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        this.passwordHash = newPasswordHash;
    }

    // ---------------------------------------------------------------
    // equals / hashCode
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
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

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UUID getLinkedPatientId() { return linkedPatientId; }
    public void setLinkedPatientId(UUID linkedPatientId) { this.linkedPatientId = linkedPatientId; }

    public String getDoctorCode() { return doctorCode; }
    public void setDoctorCode(String doctorCode) { this.doctorCode = doctorCode; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Long getVersion() { return version; }
}
