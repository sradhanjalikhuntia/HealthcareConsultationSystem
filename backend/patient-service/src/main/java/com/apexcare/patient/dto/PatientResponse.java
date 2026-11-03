package com.apexcare.patient.dto;

import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.PatientStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.apexcare.patient.entity.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientResponse {

    private UUID patientId;
    private String uniquePatientId;
    private String firstName;
    private String lastName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Gender gender;
    private String bloodGroup;
    private String phone;
    private String email;
    private String address;
    private String emergencyContact;
    private String medicalHistory;
    private String allergies;
    private PatientStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public PatientResponse() {}

    // ---- factory ----

    public static PatientResponse from(Patient p) {
        if (p == null) return null;

        PatientResponse r = new PatientResponse();
        r.patientId = p.getId();
        r.uniquePatientId = p.getUniquePatientId();
        r.firstName = p.getFirstName();
        r.lastName = p.getLastName();
        r.dateOfBirth = p.getDateOfBirth();
        r.gender = p.getGender();
        r.bloodGroup = p.getBloodGroup() == null ? null : p.getBloodGroup().name();
        r.phone = p.getPhone();
        r.email = p.getEmail();
        r.address = p.getAddress();
        r.emergencyContact = p.getEmergencyContact();
        r.medicalHistory = p.getMedicalHistory();
        r.allergies = p.getAllergies();
        r.status = p.getStatus();
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }

    // ---- getters (only one per field, no duplicates) ----

    public UUID getPatientId() { return patientId; }
    public UUID getId() { return patientId; }
    public String getUniquePatientId() { return uniquePatientId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Gender getGender() { return gender; }
    public String getBloodGroup() { return bloodGroup; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getEmergencyContact() { return emergencyContact; }
    public String getMedicalHistory() { return medicalHistory; }
    public String getAllergies() { return allergies; }
    public PatientStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
