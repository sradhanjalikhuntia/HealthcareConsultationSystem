package com.apexcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiTriageResponse {
    private String urgency;
    private String recommendedSpecialty;
    private double confidenceScore;
    private String suggestedDoctorCode;
    private List<String> possibleConditions;
    private List<String> redFlagAlerts;
    private String clinicalAdvice;
    private String disclaimer;

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getRecommendedSpecialty() { return recommendedSpecialty; }
    public void setRecommendedSpecialty(String value) { this.recommendedSpecialty = value; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double value) { this.confidenceScore = value; }
    public String getSuggestedDoctorCode() { return suggestedDoctorCode; }
    public void setSuggestedDoctorCode(String value) { this.suggestedDoctorCode = value; }
    public List<String> getPossibleConditions() { return possibleConditions; }
    public void setPossibleConditions(List<String> value) { this.possibleConditions = value; }
    public List<String> getRedFlagAlerts() { return redFlagAlerts; }
    public void setRedFlagAlerts(List<String> value) { this.redFlagAlerts = value; }
    public String getClinicalAdvice() { return clinicalAdvice; }
    public void setClinicalAdvice(String value) { this.clinicalAdvice = value; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String value) { this.disclaimer = value; }
}
