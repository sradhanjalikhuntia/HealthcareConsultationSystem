package com.apexcare.appointment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class DrugInteractionCheckRequest {

    @NotNull
    private UUID patientId;

    @NotEmpty
    @Size(max = 50)
    private List<@Size(max = 100) String> prescribedDrugs;

    @Size(max = 2000)
    private String patientAllergies;

    @Size(max = 5000)
    private String patientMedicalHistory;

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public List<String> getPrescribedDrugs() { return prescribedDrugs; }
    public void setPrescribedDrugs(List<String> prescribedDrugs) { this.prescribedDrugs = prescribedDrugs; }

    public String getPatientAllergies() { return patientAllergies; }
    public void setPatientAllergies(String patientAllergies) { this.patientAllergies = patientAllergies; }

    public String getPatientMedicalHistory() { return patientMedicalHistory; }
    public void setPatientMedicalHistory(String patientMedicalHistory) { this.patientMedicalHistory = patientMedicalHistory; }
}
