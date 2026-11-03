package com.apexcare.appointment.dto;

import jakarta.validation.constraints.NotBlank;

public class AiTriageRequest {
    @NotBlank
    private String symptoms;
    private String painLevel;

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getPainLevel() { return painLevel; }
    public void setPainLevel(String painLevel) { this.painLevel = painLevel; }
}
